/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.netdemo

import com.awakekt.awake.net.CountingViolationSink
import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.NetId
import com.awakekt.awake.net.NetViolation
import com.awakekt.awake.net.PacketReader
import com.awakekt.awake.net.PacketWriter
import com.awakekt.awake.net.SessionId
import com.awakekt.awake.net.TransportSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Authoritative, headless movement server: clients send movement intents, the server owns
 * every position and broadcasts snapshots at a fixed tick rate.
 *
 * Threading follows `samples:server`'s debug channel: socket coroutines only enqueue commands,
 * and all world mutation happens in the single tick loop. Nothing else may touch player state.
 * The codec scratch buffers below are single-threaded for the same reason.
 */
class NetDemoServer(
    private val transport: KtorWebSocketServer,
    private val tickRate: Int = DEFAULT_TICK_RATE,
    private val maxInputsPerTick: Int = DEFAULT_MAX_INPUTS_PER_TICK,
    private val moveSpeed: Float = DEFAULT_MOVE_SPEED,
    /**
     * Observe-only by default. Every check runs and reports; none of them disconnects anyone.
     * A movement bound five percent too tight kicks legitimate players on jittery mobile
     * networks, and the threshold cannot be tuned before the distribution on real traffic is
     * visible. Enforcement is a separate switch, turned on per check after the data.
     */
    val violations: CountingViolationSink = CountingViolationSink(),
) {
    val port: Int get() = transport.port

    /** Bytes broadcast since start, so a bandwidth budget can be asserted rather than assumed. */
    var bytesSent: Long = 0
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commands = Channel<ServerCommand>(Channel.UNLIMITED)
    private val players = LinkedHashMap<SessionId, Player>()
    private var nextNetId = 1L
    private var tick = 0L

    private val reader = PacketReader()
    private val input = InputBuffer()
    private val writer = PacketWriter()
    private val snapshot = SnapshotBuffer()

    fun start() {
        transport.start()
        scope.launch { acceptLoop() }
        scope.launch { tickLoop() }
    }

    suspend fun stop() {
        transport.stop()
        scope.cancel()
    }

    private suspend fun acceptLoop() {
        for (session in transport.sessions()) {
            scope.launch {
                try {
                    for (packet in session.incoming()) {
                        commands.send(ServerCommand.Packet(session, packet))
                    }
                } finally {
                    commands.send(ServerCommand.Left(session.id))
                }
            }
        }
    }

    private suspend fun tickLoop() {
        val periodMillis = (MILLIS_PER_SECOND / tickRate).toLong()
        val delta = 1f / tickRate
        while (scope.isActive) {
            drainCommands()
            applyInputs(delta)
            broadcastSnapshot()
            tick += 1
            delay(periodMillis)
        }
    }

    private fun drainCommands() {
        while (true) {
            when (val command = commands.tryReceive().getOrNull() ?: return) {
                is ServerCommand.Packet -> onPacket(command.session, command.packet)
                is ServerCommand.Left -> {
                    players.remove(command.session)
                    violations.forget(command.session)
                }
            }
        }
    }

    private fun onPacket(session: TransportSession, packet: ByteArray) {
        // Bound before decode; a hostile packet must not get to allocate.
        if (packet.size > MAX_PACKET_BYTES) {
            violations.report(session.id, NetViolation.MalformedPacket(tick, packet.size, "over-sized"))
            return
        }
        when (opcodeOf(packet)) {
            TickCodec.Opcode.JSON -> when (decodeClientFrame(packet)) {
                is ClientFrame.Join -> onJoin(session)
                null -> violations.report(session.id, NetViolation.MalformedPacket(tick, packet.size, "bad json"))
            }

            TickCodec.Opcode.INPUT -> onInput(session, packet)
            else -> violations.report(session.id, NetViolation.MalformedPacket(tick, packet.size, "unknown opcode"))
        }
    }

    private fun onInput(session: TransportSession, packet: ByteArray) {
        reader.reset(packet)
        if (!TickCodec.decodeInput(reader, input)) {
            violations.report(session.id, NetViolation.MalformedPacket(tick, packet.size, "bad input frame"))
            return
        }
        val player = players[session.id]
        if (player != null) {
            rejectionFor(input, player, tick, maxInputsPerTick)?.let { violations.report(session.id, it) }
        }
    }

    private fun onJoin(session: TransportSession) {
        if (players.containsKey(session.id)) return
        val player = Player(netId = NetId(nextNetId++), session = session)
        players[session.id] = player
        scope.launch { session.send(DeliveryChannel.Reliable, ServerFrame.Welcome(player.netId.value, tickRate).encode()) }
    }

    private fun applyInputs(delta: Float) {
        for (player in players.values) {
            player.applyQueuedInputs(moveSpeed, delta)
        }
    }

    private fun broadcastSnapshot() {
        if (players.isEmpty()) return
        snapshot.clear()
        for (player in players.values) {
            snapshot.add(player.netId.value, player.x, player.y)
        }
        TickCodec.encodeSnapshot(writer, tick, snapshot)
        val packet = writer.toPacket()
        bytesSent += packet.size.toLong() * players.size
        for (player in players.values) {
            scope.launch { player.session.send(DeliveryChannel.Unreliable, packet) }
        }
    }

    private companion object {
        const val DEFAULT_TICK_RATE = 20
        const val DEFAULT_MAX_INPUTS_PER_TICK = 4
        const val DEFAULT_MOVE_SPEED = 4f
        const val MILLIS_PER_SECOND = 1000
    }
}

/** How far from the server's own tick an input may claim to be before it is refused. */
private const val TICK_WINDOW = 60

/**
 * The whole input trust boundary, in evaluation order. Ownership first: a connection may only
 * move what it owns, and trusting whichever entity id the packet names is the most common
 * exploit in a naive implementation. Returns null when the input was accepted and queued.
 */
private fun rejectionFor(
    input: InputBuffer,
    player: Player,
    serverTick: Long,
    maxInputsPerTick: Int,
): NetViolation? = when {
    input.netId != player.netId.value -> NetViolation.NotOwner(serverTick, NetId(input.netId))

    input.tick < serverTick - TICK_WINDOW || input.tick > serverTick + TICK_WINDOW ->
        NetViolation.TickOutOfWindow(input.tick, serverTick)

    !player.queueInput(input, maxInputsPerTick) -> NetViolation.InputFlood(serverTick, maxInputsPerTick)

    else -> null
}

private sealed interface ServerCommand {
    class Packet(val session: TransportSession, val packet: ByteArray) : ServerCommand

    data class Left(val session: SessionId) : ServerCommand
}

/** Server-owned player state. Positions are world-absolute. */
private class Player(
    val netId: NetId,
    val session: TransportSession,
) {
    var x: Float = 0f
        private set
    var y: Float = 0f
        private set

    private val queuedX = FloatArray(MAX_QUEUE)
    private val queuedY = FloatArray(MAX_QUEUE)
    private var queued = 0

    /**
     * Drops inputs past [maxInputsPerTick] instead of buffering them. Buffering is what turns
     * an input flood into a speedhack: the extra inputs still get applied, just later.
     */
    fun queueInput(input: InputBuffer, maxInputsPerTick: Int): Boolean {
        if (queued >= min(maxInputsPerTick, MAX_QUEUE)) return false
        queuedX[queued] = input.moveX
        queuedY[queued] = input.moveY
        queued += 1
        return true
    }

    fun applyQueuedInputs(speed: Float, delta: Float) {
        for (index in 0 until queued) {
            val scale = unitScale(queuedX[index], queuedY[index])
            // The world is bounded, so the server clamps rather than letting a client walk
            // out of it. Open-loop input also drifts -- every dropped input breaks the orbit's
            // symmetry -- and without a bound that drift eventually leaves the play area.
            x = (x + queuedX[index] * scale * speed * delta).coerceIn(-WORLD_EXTENT, WORLD_EXTENT)
            y = (y + queuedY[index] * scale * speed * delta).coerceIn(-WORLD_EXTENT, WORLD_EXTENT)
        }
        queued = 0
    }

    /** A client may ask for any vector; the server accepts at most unit length. */
    private fun unitScale(moveX: Float, moveY: Float): Float {
        val length = sqrt(moveX * moveX + moveY * moveY)
        return if (length <= 1f) 1f else 1f / length
    }

    private companion object {
        const val MAX_QUEUE = 16

        /** Matches the grid the clients draw, so a bounded player is always visible. */
        const val WORLD_EXTENT = 6f
    }
}
