/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.ktor

import com.awakekt.awake.ecs.World
import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.TransportServer
import com.awakekt.awake.net.TransportSession
import com.awakekt.awake.net.droppedInbound
import com.awakekt.awake.net.server.codec.MessageCodec
import com.awakekt.awake.net.server.feature.Features
import com.awakekt.awake.net.server.feature.gameFeature
import com.awakekt.awake.net.server.host.HostPlayer
import com.awakekt.awake.net.server.host.ServerHost
import com.awakekt.awake.net.server.host.ServerHostConfig
import com.awakekt.awake.net.server.phase.AuthResult
import com.awakekt.awake.net.server.phase.Authenticator
import com.awakekt.awake.net.server.phase.PrePlayHandler
import com.awakekt.awake.net.server.phase.PrePlayResult
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorIntegrationTest {

    @Test
    fun peerBurstReportsActualInboundDropsAndRetainsTheNewestPackets() = testApplication {
        lateinit var transport: TransportServer
        application {
            install(GameTransport) {
                path = "/inbound-drops"
                inboxCapacity = 2
            }
            transport = gameTransport
        }
        val client = createClient { install(WebSockets) }
        client.webSocket("/inbound-drops") {
            withTimeout(5_000) {
                val session = transport.sessions().receive()
                repeat(5) { send(Frame.Binary(true, "packet:$it".encodeToByteArray())) }
                while (session.droppedInbound != 3L) delay(1)
                assertEquals("packet:3", session.incoming().receive().decodeToString())
                assertEquals("packet:4", session.incoming().receive().decodeToString())
                session.close()
                assertEquals(3L, session.droppedInbound)
            }
        }
    }

    @Test
    fun peerReceivesEveryAcceptedPacketWhenServerClosesImmediately() = testApplication {
        lateinit var transport: TransportServer
        application {
            install(GameTransport) { path = "/final-packets" }
            transport = gameTransport
        }
        val client = createClient { install(WebSockets) }
        client.webSocket("/final-packets") {
            withTimeout(5_000) {
                val session = transport.sessions().receive()
                repeat(32) { session.send(DeliveryChannel.Reliable, "final:$it".encodeToByteArray()) }
                session.close()
                val received = mutableListOf<String>()
                for (frame in incoming) {
                    if (frame is Frame.Binary) received += frame.readBytes().decodeToString()
                }
                assertEquals((0 until 32).map { "final:$it" }, received)
            }
        }
    }

    private data class Player(override val session: TransportSession, val name: String) : HostPlayer

    private sealed interface Message {
        data class Auth(val name: String) : Message
        data class Select(val slot: Int) : Message
        data class Chat(val text: String) : Message
    }

    private object Codec : MessageCodec<Message> {
        override fun decode(bytes: ByteArray): Message? {
            val str = bytes.decodeToString()
            val parts = str.split(":")
            return when (parts[0]) {
                "auth" -> Message.Auth(parts[1])
                "select" -> Message.Select(parts[1].toInt())
                "chat" -> Message.Chat(parts[1])
                else -> null
            }
        }

        override fun encode(message: Message): ByteArray = when (message) {
            is Message.Auth -> "auth:${message.name}".encodeToByteArray()
            is Message.Select -> "select:${message.slot}".encodeToByteArray()
            is Message.Chat -> "chat:${message.text}".encodeToByteArray()
        }
    }

    @Test
    fun endToEndKtorSessionPipeline() = testApplication {
        val chatMessages = mutableListOf<String>()
        var joinCount = 0

        val chatFeature = gameFeature<Player, Message>("chat") {
            onJoin { joinCount++ }
            on<Message.Chat> { player, chat ->
                chatMessages += "${player.name}: ${chat.text}"
                kotlinx.coroutines.runBlocking {
                    player.session.send(DeliveryChannel.Reliable, "echo:${chat.text}".encodeToByteArray())
                }
            }
        }

        val authenticator = Authenticator<Player, Message> { session, msg ->
            if (msg is Message.Auth) {
                AuthResult.Success(Player(session, msg.name))
            } else {
                AuthResult.Denied("Invalid auth")
            }
        }

        val prePlay = PrePlayHandler<Player, Message> { _, msg ->
            if (msg is Message.Select && msg.slot == 1) PrePlayResult.EnterPlay else PrePlayResult.StayInPrePlay
        }

        val serverHost = ServerHost(
            world = World(),
            codec = Codec,
            authenticator = authenticator,
            prePlayHandler = prePlay,
            features = Features<Player, Message>().install(chatFeature),
            config = ServerHostConfig(ticksPerSecond = 60),
        )

        try {
            application {
                install(GameTransport) {
                    path = "/game"
                }
                install(GameServerHost) {
                    host = serverHost
                }
            }

            val client = createClient {
                install(WebSockets)
            }

            client.webSocket("/game") {
                send(Frame.Binary(true, "auth:Bob".encodeToByteArray()))
                delay(50)

                send(Frame.Binary(true, "select:1".encodeToByteArray()))
                delay(50)

                send(Frame.Binary(true, "chat:HelloServer".encodeToByteArray()))

                val frame = incoming.receive() as Frame.Binary
                assertEquals("echo:HelloServer", frame.readBytes().decodeToString())
            }

            delay(50)
            assertEquals(1, joinCount)
            assertEquals(listOf("Bob: HelloServer"), chatMessages)
        } finally {
            serverHost.close()
        }
    }

    @Test
    fun slowClientDropsWhenOutboundCapacityExceeded() = testApplication {
        lateinit var transport: TransportServer
        application {
            install(GameTransport) {
                path = "/slow"
                outboundCapacity = 2
            }
            transport = gameTransport
        }

        val client = createClient {
            install(WebSockets)
        }

        client.webSocket("/slow") {
            val session = transport.sessions().receive()
            for (i in 0 until 10) {
                session.send(DeliveryChannel.Reliable, byteArrayOf(i.toByte()))
            }
            delay(100)
            val reason = closeReason.await()
            assertTrue(reason != null || incoming.isClosedForReceive)
        }
    }
}
