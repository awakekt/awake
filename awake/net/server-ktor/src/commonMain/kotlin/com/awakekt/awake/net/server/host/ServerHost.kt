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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
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
 * @property tickClock Monotonic clock and sleeper driving [TickSchedule] and [TickHealth] (defaults to [TickClock.Machine]).
 * @property onTickError Optional callback invoked when an unhandled [Exception] occurs inside a tick.
 */
@OptIn(ExperimentalAtomicApi::class)
@Suppress("TooManyFunctions", "LongParameterList")
class ServerHost<P : HostPlayer, M : Any>(
    val world: World,
    val codec: MessageCodec<M>,
    val authenticator: Authenticator<P, M>,
    val prePlayHandler: PrePlayHandler<P, M>? = null,
    val features: Features<P, M> = Features(),
    val saveScheduler: SaveScheduler<*>? = null,
    val config: ServerHostConfig = ServerHostConfig(),
    val tickDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val tickClock: TickClock = TickClock.Machine,
    val onTickError: ((tick: Long, error: Exception) -> Unit)? = null,
) : AutoCloseable {

    /** [ServerHost] before [tickClock] and [onTickError]: hidden for binary compatibility. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        world: World,
        codec: MessageCodec<M>,
        authenticator: Authenticator<P, M>,
        prePlayHandler: PrePlayHandler<P, M>? = null,
        features: Features<P, M> = Features(),
        saveScheduler: SaveScheduler<*>? = null,
        config: ServerHostConfig = ServerHostConfig(),
        tickDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : this(
        world = world,
        codec = codec,
        authenticator = authenticator,
        prePlayHandler = prePlayHandler,
        features = features,
        saveScheduler = saveScheduler,
        config = config,
        tickDispatcher = tickDispatcher,
        tickClock = TickClock.Machine,
        onTickError = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + tickDispatcher)
    private val inbound = Channel<Command<P, M>>(config.inboxCapacity, onBufferOverflow = BufferOverflow.SUSPEND)
    private val tickHealth = TickHealth(
        stalledAfterNanos = config.stalledNanos,
        failuresBeforeUnhealthy = config.failuresBeforeUnhealthy,
        clock = tickClock,
    )

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

    private val _droppedTicksCount = AtomicLong(0L)

    /** Total simulation ticks abandoned because catch-up backlog exceeded [ServerHostConfig.maxBacklogTicks]. */
    val droppedTicksCount: Long get() = _droppedTicksCount.load()

    private val _caughtUpTicksCount = AtomicLong(0L)

    /** Total overdue simulation ticks executed back-to-back without sleeping to catch up to wall time. */
    val caughtUpTicksCount: Long get() = _caughtUpTicksCount.load()

    @Volatile
    private var stopping = false

    private var tickJob: Job? = null

    /** Current count of players in the active [SessionPhase.Playing] phase. */
    val onlineCount: Int
        get() = sessions.load().let { s -> s.players.values.count { s.phases[it.session.id] == SessionPhase.Playing } }

    /**
     * Whether the host loop is currently healthy: not stopping, has ticked within [ServerHostConfig.stalledNanos],
     * and has fewer than [ServerHostConfig.failuresBeforeUnhealthy] consecutive failed ticks.
     *
     * @return `true` while the tick loop is healthy.
     */
    fun healthy(): Boolean = !stopping && tickHealth.healthy()

    /**
     * Diagnostic reason why [healthy] is `false`, or `null` while healthy.
     *
     * @return Human-readable unhealthy reason, or `null` when healthy.
     */
    fun unhealthyReason(): String? =
        if (stopping) TickHealth.REASON_STOPPED else tickHealth.unhealthyReason()

    /**
     * Starts the host simulation tick loop.
     *
     * @return Coroutine [Job] driving the fixed-rate tick schedule.
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
     *
     * @param session Accepted client transport session.
     * @return Coroutine [Job] pumping packets for [session].
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
        val schedule = TickSchedule(
            ticksPerSecond = config.ticksPerSecond,
            maxBacklogTicks = config.maxBacklogTicks,
            startNanos = tickClock.nanoTime(),
        )
        val delta = 1f / config.ticksPerSecond
        var slept = true

        try {
            while (scope.isActive && !stopping) {
                val began = tickClock.nanoTime()
                if (!schedule.due(began)) {
                    tickClock.sleepUntil(schedule.nextAt)
                    slept = true
                } else {
                    val dropped = schedule.begin(began)
                    if (dropped > 0L) {
                        _droppedTicksCount.addAndFetch(dropped)
                    }
                    if (!slept) {
                        _caughtUpTicksCount.addAndFetch(1L)
                    }
                    slept = false
                    executeTick(delta)
                    if (schedule.due(tickClock.nanoTime())) {
                        yield()
                    }
                }
            }
        } finally {
            tickHealth.ended()
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
     *
     * @param delta Elapsed simulation step in seconds.
     */
    fun step(delta: Float) {
        executeTick(delta)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun executeTick(delta: Float) {
        val startedNanos = hostNanoTime()
        var failed = false
        try {
            drainCommands()
            features.ticked(delta)
            features.beforeSnapshot()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failed = true
            onTickError?.invoke(tick, error)
        }
        tickHealth.ticked(failed)
        tickTimeNanos = hostNanoTime() - startedNanos
        tick++
    }

    /**
     * Enqueues a message directly to the host for [player].
     *
     * @param player Active player session.
     * @param message Decoded message payload.
     * @return `true` if queued, or `false` if dropped because the inbound queue was full.
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
     *
     * @param player Player entering active play.
     */
    fun join(player: P) {
        enterPlay(player)
    }

    /**
     * Directly marks [player] as having left active play.
     *
     * @param player Player leaving active play.
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
        tickHealth.ended()
        tickJob?.cancel()
        saveScheduler?.flush(FLUSH_TIMEOUT_MILLIS)
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

    private companion object {
        const val FLUSH_TIMEOUT_MILLIS = 3_000L
    }
}
