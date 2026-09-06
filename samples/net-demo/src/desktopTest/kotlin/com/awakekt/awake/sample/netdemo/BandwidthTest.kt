/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.netdemo

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

private const val TEST_TIMEOUT_MILLIS = 60_000L

/**
 * Phase 2's bandwidth criterion: 16 clients at 20 Hz, and a per-client budget that is measured
 * rather than hoped for.
 *
 * The number this pins down is the one that decides whether a server holds 16 players or 200.
 * Broadcasting full snapshots is O(players^2) in total bytes -- every client receives every
 * entity -- which is exactly why interest management, not a bigger pipe, is the fix.
 */
class BandwidthTest {
    private val server = NetDemoServer(KtorWebSocketServer())

    @AfterTest
    fun tearDown() = runBlocking { server.stop() }

    @Test
    fun `sixteen clients stay inside the per-client budget`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            server.start()
            val clients = (1..CLIENTS).map { index ->
                NetDemoClient(KtorWebSocketClient(port = server.port)).also {
                    it.connect("client-$index")
                    it.receiveUntil { client -> client.netId != null }
                }
            }
            clients.forEach { client -> client.receiveUntil { it.snapshot.count == CLIENTS } }

            val startBytes = server.bytesSent
            val startTick = clients.first().snapshot.tick
            clients.first().receiveUntil { it.snapshot.tick >= startTick + MEASURED_TICKS }
            val sentBytes = server.bytesSent - startBytes

            val seconds = MEASURED_TICKS.toDouble() / TICK_RATE
            val perClientBytesPerSecond = sentBytes / seconds / CLIENTS
            println(
                "bandwidth: $CLIENTS clients, $sentBytes bytes over ${seconds}s " +
                    "= ${perClientBytesPerSecond.toInt()} B/s per client",
            )
            assertTrue(
                perClientBytesPerSecond < MAX_BYTES_PER_SECOND_PER_CLIENT,
                "per-client bandwidth regressed to ${perClientBytesPerSecond.toInt()} B/s",
            )
            clients.forEach { it.close() }
        }
    }

    private companion object {
        const val CLIENTS = 16
        const val TICK_RATE = 20
        const val MEASURED_TICKS = 40

        /**
         * A 16-entity snapshot is ~100 bytes, sent 20x per second, so ~2 KB/s per client is the
         * expected shape. The ceiling is generous because this guards against a regression --
         * a field added to every entity, or quantization dropped -- not against a tuned target.
         */
        const val MAX_BYTES_PER_SECOND_PER_CLIENT = 6_000
    }
}
