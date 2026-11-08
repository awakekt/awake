/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.host

import com.awakekt.awake.ecs.World
import com.awakekt.awake.net.TransportSession
import com.awakekt.awake.net.server.codec.MessageCodec
import com.awakekt.awake.net.server.feature.Features
import com.awakekt.awake.net.server.feature.gameFeature
import com.awakekt.awake.net.server.phase.AuthResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerHostTickLoopTest {
    private data class DummyPlayer(override val session: TransportSession) : HostPlayer

    private object NoopCodec : MessageCodec<String> {
        override fun decode(bytes: ByteArray): String = bytes.decodeToString()
        override fun encode(message: String): ByteArray = message.encodeToByteArray()
    }

    private fun buildHost(
        clock: ManualTickClock,
        shouldThrow: () -> Boolean = { false },
        onTickError: (Long, Throwable) -> Unit = { _, _ -> },
    ): ServerHost<DummyPlayer, String> {
        val feature = gameFeature<DummyPlayer, String>("sim") {
            onTick {
                if (shouldThrow()) {
                    error("simulated feature failure")
                }
            }
        }
        return ServerHost(
            world = World(),
            codec = NoopCodec,
            authenticator = { session, _ -> AuthResult.Success(DummyPlayer(session)) },
            features = Features<DummyPlayer, String>().install(feature),
            config = ServerHostConfig(
                ticksPerSecond = 20,
                maxBacklogTicks = 3,
                stalledNanos = 500_000_000L,
                failuresBeforeUnhealthy = 2,
            ),
            tickClock = clock,
            onTickError = onTickError,
        )
    }

    @Test
    fun manualTickClockDrivesCatchUpAndBacklogCapping() {
        val clock = ManualTickClock(start = 1_000_000L)
        val host = buildHost(clock)

        host.start()
        try {
            assertTrue(clock.awaitSleeping(2_000L))
            assertEquals(1L, host.tick)
            assertTrue(host.healthy())
            assertNull(host.unhealthyReason())

            // Advance by 2 periods (100ms): runs 2 ticks back-to-back (1 scheduled + 1 catch-up).
            clock.advanceBy(100_000_000L)
            assertTrue(clock.awaitSleeping(2_000L))
            assertEquals(3L, host.tick)
            assertEquals(1L, host.caughtUpTicksCount)
            assertEquals(0L, host.droppedTicksCount)

            // Stall 10 periods (500ms) with maxBacklogTicks = 3: drops 6 overdue ticks, runs 4.
            clock.advanceBy(500_000_000L)
            assertTrue(clock.awaitSleeping(2_000L))
            assertEquals(7L, host.tick)
            assertEquals(6L, host.droppedTicksCount)
            assertEquals(4L, host.caughtUpTicksCount)
        } finally {
            host.close()
        }
        assertFalse(host.healthy())
        assertEquals(TickHealth.REASON_STOPPED, host.unhealthyReason())
    }

    @Test
    fun consecutiveTickFailuresFlipHealthUntilCleanTick() {
        val clock = ManualTickClock(start = 1_000_000L)
        var shouldThrow = false
        val errors = mutableListOf<Long>()
        val host = buildHost(clock, shouldThrow = { shouldThrow }, onTickError = { idx, _ -> errors += idx })

        host.start()
        try {
            assertTrue(clock.awaitSleeping(2_000L))

            shouldThrow = true
            clock.advanceBy(50_000_000L)
            assertTrue(clock.awaitSleeping(2_000L))
            assertTrue(host.healthy())

            clock.advanceBy(50_000_000L)
            assertTrue(clock.awaitSleeping(2_000L))
            assertFalse(host.healthy())
            assertEquals("the last 2 ticks failed", host.unhealthyReason())
            assertEquals(2, errors.size)

            shouldThrow = false
            clock.advanceBy(50_000_000L)
            assertTrue(clock.awaitSleeping(2_000L))
            assertTrue(host.healthy())
        } finally {
            host.close()
        }
    }
}
