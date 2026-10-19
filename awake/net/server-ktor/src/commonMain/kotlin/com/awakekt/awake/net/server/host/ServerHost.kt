/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.host

import com.awakekt.awake.ecs.World
import com.awakekt.awake.net.SessionId
import com.awakekt.awake.net.TransportSession
import com.awakekt.awake.net.server.codec.MessageCodec
import com.awakekt.awake.net.server.feature.Features
import com.awakekt.awake.net.server.phase.AuthResult
import com.awakekt.awake.net.server.phase.Authenticator
import com.awakekt.awake.net.server.phase.PrePlayHandler
import com.awakekt.awake.net.server.phase.PrePlayResult
import com.awakekt.awake.net.server.phase.SessionPhase
import com.awakekt.awake.net.server.save.SaveScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Authoritative, single-thread game host running the fixed simulation tick loop and routing incoming messages
 * to installed [Features].
 *
 * @param P The player model representing an active session.
 * @param M The base message type.
 * @property world The ECS [World] instance for this game host.
 * @property codec Serializer and deserializer for raw transport packets.
 * @property authenticator Pluggable credential verification for new connections.
 * @property prePlayHandler Optional pre-play handler (e.g. character select/lobby) before active play begins.
 * @property features Container of installed game features.
 * @property saveScheduler Optional save coordinator for batched persistence.
 * @property config Host tuning configuration.
 * @property tickDispatcher Dispatcher on which the simulation tick executes (defaults to [Dispatchers.Default]).
 */
@OptIn(ExperimentalAtomicApi::class)
@Suppress("TooManyFunctions")
class ServerHost<P : HostPlayer, M : Any>(
    val world: World,
    val codec: MessageCodec<M>,
    val authenticator: Authenticator<P, M>,
    val prePlayHandler: PrePlayHandler<P, M>? = null,
    val features: Features<P, M> = Features(),
    val saveScheduler: SaveScheduler<*>? = null,
    val config: ServerHostConfig = ServerHostConfig(),
    val tickDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + tickDispatcher)
    private val inbound = Channel<Command<P, M>>(config.inboxCapacity, onBufferOverflow = BufferOverflow.SUSPEND)

    private val sessions = AtomicReference(HostSessions<P>())

    /** Current server simulation tick count. */
    var tick: Long = 0
        private set

    /** Duration of the most recent tick in nanoseconds. */
    var tickTimeNanos: Long = 0
        private set

    private val _droppedInputsCount = AtomicLong(0L)

    /** Total inputs dropped due to inbound queue overflow. */
    val droppedInputsCount: Long get() = _droppedInputsCount.load()

    @Volatile
    private var tickedAt = 0L

    @Volatile
    private var stopping = false

    private var tickJob: Job? = null

    /** Current count of players in the active [SessionPhase.Playing] phase. */
    val onlineCount: Int
        get() = sessions.load().let { s -> s.players.values.count { s.phases[it.session.id] == SessionPhase.Playing } }

    /**
     * Whether the host loop is currently healthy: not stopping, has ticked, and hasn't stalled longer than [ServerHostConfig.stalledNanos].
     */
    fun healthy(): Boolean =
        !stopping && tick > 0 && (hostNanoTime() - tickedAt) < config.stalledNanos

    /**
     * Starts the host simulation tick loop.
     */
    fun start(): Job {
        val job = scope.launch(tickDispatcher) {
            tickLoop()
        }
        tickJob = job
        return job
    }

    /**
     * Attaches [session] to the host, reading its packets and guiding it through the phase pipeline.
     */
    fun serve(session: TransportSession): Job = scope.launch(Dispatchers.Default) {
        updateSessions { it.copy(phases = it.phases + (session.id to SessionPhase.Accept)) }
        try {
            for (packet in session.incoming()) {
                val message = codec.decode(packet) ?: continue
                handlePacket(session, message)
            }
        } finally {
            var leftPlayer: P? = null
            updateSessions { s ->
                val player = s.players[session.id]
                val previousPhase = s.phases[session.id]
                if (player != null && previousPhase == SessionPhase.Playing) {
                    leftPlayer = player
                }
                s.copy(players = s.players - session.id, phases = s.phases - session.id)
            }
            leftPlayer?.let { inbound.trySend(Command.Left(it)) }
        }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private suspend fun handlePacket(session: TransportSession, message: M) {
        val phase = sessions.load().phases[session.id] ?: return
        when (phase) {
            SessionPhase.Accept -> {
                updateSessions { it.copy(phases = it.phases + (session.id to SessionPhase.Authenticating)) }
                val result = authenticator.authenticate(session, message)
                when (result) {
                    is AuthResult.Success -> {
                        val player = result.player
                        if (prePlayHandler != null) {
                            updateSessions {
                                it.copy(
                                    players = it.players + (session.id to player),
                                    phases = it.phases + (session.id to SessionPhase.PrePlay),
                                )
                            }
                        } else {
                            enterPlay(player)
                        }
                    }
                    is AuthResult.Denied -> {
                        updateSessions { it.copy(phases = it.phases + (session.id to SessionPhase.Closed)) }
                        session.close()
                    }
                }
            }
            SessionPhase.PrePlay -> {
                val player = sessions.load().players[session.id] ?: return
                val result = prePlayHandler?.handlePrePlay(player, message)
                when (result) {
                    PrePlayResult.EnterPlay -> enterPlay(player)
                    is PrePlayResult.Refused -> {
                        updateSessions { it.copy(phases = it.phases + (session.id to SessionPhase.Closed)) }
                        session.close()
                    }
                    PrePlayResult.StayInPrePlay, null -> Unit
                }
            }
            SessionPhase.Playing -> {
                val player = sessions.load().players[session.id] ?: return
                val sent = inbound.trySend(Command.Packet(player, message))
                if (sent.isFailure) {
                    _droppedInputsCount.addAndFetch(1L)
                }
            }
            SessionPhase.Authenticating, SessionPhase.Closed -> Unit
        }
    }

    private fun enterPlay(player: P) {
        updateSessions {
            it.copy(
                players = it.players + (player.session.id to player),
                phases = it.phases + (player.session.id to SessionPhase.Playing),
            )
        }
        inbound.trySend(Command.Joined(player))
    }

    private suspend fun tickLoop() {
        val periodMillis = (1000L / config.ticksPerSecond).coerceAtLeast(1)
        val delta = 1f / config.ticksPerSecond

        while (scope.isActive && !stopping) {
            val startedNanos = hostNanoTime()
            val startedMillis = hostCurrentTimeMillis()

            drainCommands()
            features.ticked(delta)

            tickedAt = hostNanoTime()
            tickTimeNanos = tickedAt - startedNanos
            tick++

            val elapsedMillis = hostCurrentTimeMillis() - startedMillis
            val sleepMillis = periodMillis - elapsedMillis
            if (sleepMillis > 0) {
                delay(sleepMillis)
            }
        }
    }

    /**
     * Drains pending commands from the inbound queue.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun drainCommands() {
        if (inbound.isEmpty) return
        var processed = 0
        while (processed < config.inboxCapacity && !inbound.isEmpty) {
            val cmd = inbound.tryReceive().getOrNull() ?: break
            when (cmd) {
                is Command.Joined -> features.joined(cmd.player)
                is Command.Left -> features.left(cmd.player)
                is Command.Packet -> features.route(cmd.player, cmd.message)
            }
            processed++
        }
    }

    /**
     * Steps the simulation once with [delta] seconds elapsed, without a background loop.
     * Useful for deterministic headless unit tests.
     */
    fun step(delta: Float) {
        val startedNanos = hostNanoTime()
        drainCommands()
        features.ticked(delta)
        tickedAt = hostNanoTime()
        tickTimeNanos = tickedAt - startedNanos
        tick++
    }

    /**
     * Enqueues a message directly to the host for [player].
     */
    fun submit(player: P, message: M): Boolean {
        val success = inbound.trySend(Command.Packet(player, message)).isSuccess
        if (!success) {
            _droppedInputsCount.addAndFetch(1L)
        }
        return success
    }

    /**
     * Directly marks [player] as having joined active play.
     */
    fun join(player: P) {
        enterPlay(player)
    }

    /**
     * Directly marks [player] as having left active play.
     */
    fun leave(player: P) {
        var wasPlaying = false
        updateSessions { s ->
            wasPlaying = s.phases[player.session.id] == SessionPhase.Playing
            s.copy(
                players = s.players - player.session.id,
                phases = s.phases - player.session.id,
            )
        }
        if (wasPlaying) {
            inbound.trySend(Command.Left(player))
        }
    }

    override fun close() {
        stopping = true
        tickJob?.cancel()
        saveScheduler?.flush(3000L)
        saveScheduler?.close()
        inbound.close()
        scope.cancel()
    }

    private inline fun updateSessions(transform: (HostSessions<P>) -> HostSessions<P>) {
        while (true) {
            val current = sessions.load()
            val next = transform(current)
            if (sessions.compareAndSet(current, next)) return
        }
    }

    private data class HostSessions<P>(
        val players: Map<SessionId, P> = emptyMap(),
        val phases: Map<SessionId, SessionPhase> = emptyMap(),
    )

    private sealed interface Command<out P, out M> {
        class Joined<P>(val player: P) : Command<P, Nothing>
        class Left<P>(val player: P) : Command<P, Nothing>
        class Packet<P, M>(val player: P, val message: M) : Command<P, M>
    }
}
