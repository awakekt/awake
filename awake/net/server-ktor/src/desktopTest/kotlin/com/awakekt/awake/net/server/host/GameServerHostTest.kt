/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.host

import com.awakekt.awake.ecs.World
import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.SessionId
import com.awakekt.awake.net.TransportSession
import com.awakekt.awake.net.server.codec.MessageCodec
import com.awakekt.awake.net.server.feature.Features
import com.awakekt.awake.net.server.feature.gameFeature
import com.awakekt.awake.net.server.phase.AuthResult
import com.awakekt.awake.net.server.phase.Authenticator
import com.awakekt.awake.net.server.phase.PrePlayHandler
import com.awakekt.awake.net.server.phase.PrePlayResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameServerHostTest {

    private data class Player(override val session: TransportSession, val name: String) : HostPlayer

    private sealed interface Message {
        data class Auth(val name: String) : Message
        data class SelectSlot(val slot: Int) : Message
        data class Move(val x: Float, val y: Float) : Message
    }

    private class FakeSession(override val id: SessionId) : TransportSession {
        val sent = mutableListOf<ByteArray>()
        val inbox = Channel<ByteArray>(Channel.UNLIMITED)
        var closed = false

        override suspend fun send(channel: DeliveryChannel, packet: ByteArray) {
            sent += packet
        }

        override fun incoming(): ReceiveChannel<ByteArray> = inbox

        override suspend fun close() {
            closed = true
            inbox.close()
        }
    }

    private object Codec : MessageCodec<Message> {
        override fun decode(bytes: ByteArray): Message? {
            val str = bytes.decodeToString()
            val parts = str.split(":")
            return when (parts[0]) {
                "auth" -> Message.Auth(parts[1])
                "select" -> Message.SelectSlot(parts[1].toInt())
                "move" -> Message.Move(parts[1].toFloat(), parts[2].toFloat())
                else -> null
            }
        }

        override fun encode(message: Message): ByteArray = when (message) {
            is Message.Auth -> "auth:${message.name}".encodeToByteArray()
            is Message.SelectSlot -> "select:${message.slot}".encodeToByteArray()
            is Message.Move -> "move:${message.x}:${message.y}".encodeToByteArray()
        }
    }

    @Test
    fun sessionTransitionsThroughAuthPrePlayAndPlayPhases() = runBlocking {
        val world = World()
        val moves = mutableListOf<Pair<String, Message.Move>>()
        var joined = 0
        var left = 0

        val movementFeature = gameFeature<Player, Message>("movement") {
            onJoin { joined++ }
            onLeave { left++ }
            on<Message.Move> { player, move ->
                moves += (player.name to move)
            }
        }

        val authenticator = Authenticator<Player, Message> { session, msg ->
            if (msg is Message.Auth && msg.name != "banned") {
                AuthResult.Success(Player(session, msg.name))
            } else {
                AuthResult.Denied("Rejected")
            }
        }

        val prePlay = PrePlayHandler<Player, Message> { _, msg ->
            if (msg is Message.SelectSlot && msg.slot == 1) {
                PrePlayResult.EnterPlay
            } else {
                PrePlayResult.StayInPrePlay
            }
        }

        val host = ServerHost(
            world = world,
            codec = Codec,
            authenticator = authenticator,
            prePlayHandler = prePlay,
            features = Features<Player, Message>().install(movementFeature),
            config = ServerHostConfig(ticksPerSecond = 30),
        )

        val session = FakeSession(SessionId(101))
        val job = host.serve(session)

        // 1. Send auth
        session.inbox.send("auth:Alice".encodeToByteArray())
        delay(20)
        host.step(0.033f)
        assertEquals(0, host.onlineCount) // In PrePlay, not yet active play

        // 2. Send pre-play selection
        session.inbox.send("select:1".encodeToByteArray())
        delay(20)
        host.step(0.033f)
        assertEquals(1, host.onlineCount)
        assertEquals(1, joined)

        // 3. Send movement in Play phase
        session.inbox.send("move:10.5:20.0".encodeToByteArray())
        delay(20)
        host.step(0.033f)
        assertEquals(1, moves.size)
        assertEquals("Alice", moves[0].first)
        assertEquals(10.5f, moves[0].second.x)
        assertEquals(20.0f, moves[0].second.y)

        // 4. Disconnect session
        session.close()
        job.join()
        host.step(0.033f)
        assertEquals(0, host.onlineCount)
        assertEquals(1, left)

        host.close()
    }

    @Test
    fun steadyTicksAllocateNothingInHost() {
        val world = World()
        val ticksHolder = IntArray(1)
        val dummyFeature = gameFeature<Player, Message>("dummy") {
            onTick { ticksHolder[0]++ }
        }

        val host = ServerHost(
            world = world,
            codec = Codec,
            authenticator = { session, _ -> AuthResult.Success(Player(session, "test")) },
            features = Features<Player, Message>().install(dummyFeature),
            config = ServerHostConfig(ticksPerSecond = 30),
        )

        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        if (bean != null && bean.isThreadAllocatedMemorySupported) {
            // Warm up JIT and classloading to reach C2 compilation
            repeat(10_000) {
                host.step(0.033f)
            }

            val iterations = 1000
            val allocatedBefore = bean.currentThreadAllocatedBytes
            repeat(iterations) {
                host.step(0.033f)
            }
            val allocatedAfter = bean.currentThreadAllocatedBytes
            val allocatedBytesPerTick = (allocatedAfter - allocatedBefore) / iterations

            assertEquals(0L, allocatedBytesPerTick, "Steady host step ticks must allocate 0 bytes per tick in steady state, but allocated $allocatedBytesPerTick bytes/tick.")
        }

        host.close()
    }

    @Test
    fun fullQueueDropsInputsAndIncrementsMetric() {
        val world = World()
        val host = ServerHost(
            world = world,
            codec = Codec,
            authenticator = { session, _ -> AuthResult.Success(Player(session, "test")) },
            features = Features<Player, Message>(),
            config = ServerHostConfig(inboxCapacity = 4),
        )

        val player = Player(FakeSession(SessionId(1)), "p1")
        host.join(player)
        host.drainCommands()

        // Submit up to capacity
        for (i in 0 until 4) {
            assertTrue(host.submit(player, Message.Move(i.toFloat(), 0f)))
        }

        // Submitting beyond capacity causes drop
        val overflowSuccess = host.submit(player, Message.Move(99f, 0f))
        assertFalse(overflowSuccess)
        assertEquals(1L, host.droppedInputsCount)

        host.close()
    }
}
