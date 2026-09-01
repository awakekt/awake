/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.netdemo

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TEST_TIMEOUT_MILLIS = 10_000L

class NetDemoServerTest {
    private val server = NetDemoServer(KtorWebSocketServer())

    @AfterTest
    fun tearDown() = runBlocking { server.stop() }

    /** The Phase 0 exit criterion: two clients, and each sees the other move. */
    @Test
    fun `second client sees the first client move`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            server.start()
            val mover = connectClient("mover")
            val observer = connectClient("observer")

            val moverId = assertNotNull(mover.netId, "mover never received Welcome")
            observer.receiveUntil { it.snapshot.count == 2 }
            val moverIndex = observer.indexOf(moverId)
            val startX = observer.snapshot.x[moverIndex]
            val startY = observer.snapshot.y[moverIndex]

            repeat(TICKS_OF_INPUT) { tick ->
                mover.sendInput(tick.toLong(), moveX = 1f, moveY = 0f)
            }
            observer.receiveUntil { it.snapshot.x[it.indexOf(moverId)] > startX }

            val endIndex = observer.indexOf(moverId)
            assertTrue(observer.snapshot.x[endIndex] > startX, "observer saw no movement")
            assertEquals(startY, observer.snapshot.y[endIndex], "movement leaked into the unrequested axis")

            mover.close()
            observer.close()
        }
    }

    /** A client may request any vector; the server must accept at most unit length per input. */
    @Test
    fun `oversized movement input is clamped`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            server.start()
            val cheater = connectClient("cheater")
            val id = assertNotNull(cheater.netId)

            cheater.sendInput(tick = 0, moveX = HUGE_MOVE, moveY = 0f)
            cheater.receiveUntil { it.snapshot.count == 1 && it.snapshot.x[0] > 0f }

            assertTrue(cheater.snapshot.x[0] < MAX_PLAUSIBLE_X, "clamp did not hold: x=${cheater.snapshot.x[0]}")
            assertEquals(id.value, cheater.snapshot.netIds[0])
            cheater.close()
        }
    }

    private suspend fun connectClient(name: String): NetDemoClient {
        val client = NetDemoClient(KtorWebSocketClient(port = server.port))
        client.connect(name)
        client.receiveUntil { it.netId != null }
        return client
    }

    private companion object {
        const val TICKS_OF_INPUT = 20
        const val HUGE_MOVE = 10_000f

        /** One clamped unit input at the default 4 m/s over a 20 Hz tick moves 0.2 m. */
        const val MAX_PLAUSIBLE_X = 1f
    }
}
