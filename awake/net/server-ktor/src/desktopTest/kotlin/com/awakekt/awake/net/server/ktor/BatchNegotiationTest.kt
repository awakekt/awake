/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.ktor

import com.awakekt.awake.net.BatchFrame
import com.awakekt.awake.net.BatchedSession
import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.TransportServer
import com.awakekt.awake.net.acceptsBatches
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatchNegotiationTest {
    private val ticks = listOf("one", "two", "three")

    private suspend fun BatchedSession.produceTick() {
        ticks.forEach { send(DeliveryChannel.Reliable, it.encodeToByteArray()) }
        flush()
    }

    @Test
    fun clientThatAsksReceivesOneBatchFrameCarryingTheTicksMessagesInOrder() = testApplication {
        lateinit var transport: TransportServer
        application {
            install(GameTransport) {
                path = "/net"
                batching = true
            }
            transport = gameTransport
        }
        val client = createClient { install(WebSockets) }
        client.webSocket("/net?${BatchFrame.NEGOTIATION_PARAMETER}=${BatchFrame.NEGOTIATION_VALUE}") {
            withTimeout(5_000) {
                val session = BatchedSession(transport.sessions().receive())
                assertTrue(session.enabled)
                session.produceTick()
                val frame = incoming.receive() as Frame.Binary
                assertEquals(ticks, BatchFrame.decode(frame.readBytes())!!.map { it.decodeToString() })
            }
        }
    }

    @Test
    fun oldStyleClientAgainstABatchingServerKeepsGettingOneFramePerMessage() = testApplication {
        lateinit var transport: TransportServer
        application {
            install(GameTransport) {
                path = "/net"
                batching = true
            }
            transport = gameTransport
        }
        val client = createClient { install(WebSockets) }
        client.webSocket("/net") {
            withTimeout(5_000) {
                val raw = transport.sessions().receive()
                assertFalse(raw.acceptsBatches)
                BatchedSession(raw).produceTick()
                val received = ticks.map { (incoming.receive() as Frame.Binary).readBytes().decodeToString() }
                assertEquals(ticks, received)
            }
        }
    }

    @Test
    fun askingIsIgnoredWhenTheServerDidNotEnableBatching() = testApplication {
        lateinit var transport: TransportServer
        application {
            install(GameTransport) { path = "/net" }
            transport = gameTransport
        }
        val client = createClient { install(WebSockets) }
        client.webSocket("/net?${BatchFrame.NEGOTIATION_PARAMETER}=${BatchFrame.NEGOTIATION_VALUE}") {
            withTimeout(5_000) { assertFalse(transport.sessions().receive().acceptsBatches) }
        }
    }
}
