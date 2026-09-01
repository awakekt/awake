/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.netdemo

import io.github.awakelab.awake.net.DeliveryChannel
import io.github.awakelab.awake.net.PacketWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TEST_TIMEOUT_MILLIS = 20_000L

/** Phase 2's security criteria from docs/plans/network.md, each asserted rather than assumed. */
class ViolationEnforcementTest {
    private val server = NetDemoServer(KtorWebSocketServer())

    @AfterTest
    fun tearDown() = runBlocking { server.stop() }

    /**
     * The most common exploit in a naive implementation: address someone else's entity. The
     * server must refuse it and must not move the victim.
     */
    @Test
    fun `input addressing another entity is rejected`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            server.start()
            val victim = connect("victim")
            val attacker = connect("attacker")
            val victimId = assertNotNull(victim.netId)
            attacker.receiveUntil { it.snapshot.count == 2 }
            val victimStartX = victim.snapshot.x[victim.indexOf(victimId)]

            // Forge inputs for the victim's entity, from the attacker's connection.
            val forged = PacketWriter()
            repeat(FORGED_INPUTS) {
                TickCodec.encodeInput(forged, victimId.value, tick = 0, moveX = 1f, moveY = 0f)
                attacker.transport.send(DeliveryChannel.Unreliable, forged.toPacket())
                delay(TICK_MILLIS)
            }
            victim.receiveUntil { it.snapshot.tick > 0 }

            val victimNowX = victim.snapshot.x[victim.indexOf(victimId)]
            assertEquals(victimStartX, victimNowX, "the victim was moved by someone else's input")
            assertTrue(
                server.violations.count(attackerSession(), "not-owner") > 0,
                "forged ownership was not reported",
            )
            victim.close()
            attacker.close()
        }
    }

    /** Inputs past the per-tick cap are dropped and counted, not buffered into a speedhack. */
    @Test
    fun `input flood is reported`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            server.start()
            val client = connect("flooder")
            val id = assertNotNull(client.netId)

            val writer = PacketWriter()
            repeat(FLOOD_INPUTS) {
                TickCodec.encodeInput(writer, id.value, tick = 0, moveX = 1f, moveY = 0f)
                client.transport.send(DeliveryChannel.Unreliable, writer.toPacket())
            }
            client.receiveUntil { it.snapshot.tick > 2 }

            assertTrue(server.violations.total() > 0, "a flood of ${FLOOD_INPUTS} inputs went unreported")
            client.close()
        }
    }

    /**
     * Unbounded logging is itself the denial of service. Counters may climb; retained detail
     * must not.
     */
    @Test
    fun `a packet flood does not grow retained detail without bound`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            server.start()
            val client = connect("garbage")

            repeat(GARBAGE_PACKETS) {
                client.transport.send(DeliveryChannel.Unreliable, byteArrayOf(0x7F, 1, 2, 3))
            }
            client.receiveUntil { it.snapshot.tick > 2 }

            val retained = server.violations.retainedSamples()
            assertTrue(server.violations.total() > 0, "garbage packets went unreported")
            assertTrue(
                retained <= MAX_RETAINED,
                "retained detail grew to $retained; a flood would fill the log pipeline",
            )
            client.close()
        }
    }

    private suspend fun connect(name: String): NetDemoClient {
        val client = NetDemoClient(KtorWebSocketClient(port = server.port))
        client.connect(name)
        client.receiveUntil { it.netId != null }
        return client
    }

    /** The attacker is the second session the server accepted. */
    private fun attackerSession() = io.github.awakelab.awake.net.SessionId(2)

    private companion object {
        const val FORGED_INPUTS = 10
        const val FLOOD_INPUTS = 200
        const val GARBAGE_PACKETS = 500
        const val TICK_MILLIS = 50L

        /** Five samples per kind, five kinds; anything above that is a leak. */
        const val MAX_RETAINED = 25
    }
}
