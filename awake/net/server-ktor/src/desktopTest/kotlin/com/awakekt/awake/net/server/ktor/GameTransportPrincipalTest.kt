/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.ktor

import com.awakekt.awake.net.TransportServer
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.ktor.util.AttributeKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameTransportPrincipalTest {
    private val verifiedPeer = AttributeKey<String>("VerifiedPeer")

    /** Stands in for a check that verifies the upgrade request and records who it verified. */
    private val testGate = createApplicationPlugin("TestGate") {
        onCall { call ->
            call.request.headers["X-Test-Peer"]?.let { call.attributes.put(verifiedPeer, it) }
        }
    }

    @Test
    fun principalResolvedFromTheUpgradeRequestReachesTheHost() = testApplication {
        lateinit var transport: TransportServer
        application {
            install(testGate)
            install(GameTransport) {
                path = "/net"
                principal = { call -> call.attributes.getOrNull(verifiedPeer) }
            }
            transport = gameTransport
        }

        val client = createClient { install(WebSockets) }

        client.webSocket("/net", request = { header("X-Test-Peer", "peer-42") }) {
            assertEquals("peer-42", transport.sessions().receive().principal)
        }
        client.webSocket("/net") {
            assertNull(transport.sessions().receive().principal)
        }
    }

    @Test
    fun withoutAResolverSessionsHaveNoPrincipal() = testApplication {
        lateinit var transport: TransportServer
        application {
            install(GameTransport) { path = "/net" }
            transport = gameTransport
        }

        val client = createClient { install(WebSockets) }

        client.webSocket("/net") {
            assertNull(transport.sessions().receive().principal)
        }
    }
}
