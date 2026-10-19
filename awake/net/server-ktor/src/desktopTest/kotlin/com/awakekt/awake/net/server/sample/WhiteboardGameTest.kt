/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.sample

import com.awakekt.awake.ecs.World
import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.SessionId
import com.awakekt.awake.net.TransportSession
import com.awakekt.awake.net.server.codec.MessageCodec
import com.awakekt.awake.net.server.feature.Features
import com.awakekt.awake.net.server.feature.gameFeature
import com.awakekt.awake.net.server.host.HostPlayer
import com.awakekt.awake.net.server.host.ServerHost
import com.awakekt.awake.net.server.host.ServerHostConfig
import com.awakekt.awake.net.server.phase.AuthResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Validates that an interactive, non-combat real-time application (a collaborative whiteboard)
 * runs on [ServerHost] and the [Features] API with zero modifications to the core server module.
 */
class WhiteboardGameTest {

    private data class WhiteboardUser(
        override val session: TransportSession,
        val username: String,
    ) : HostPlayer

    private data class Stroke(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int)

    private sealed interface WhiteboardMessage {
        data class Auth(val user: String) : WhiteboardMessage
        data class Draw(val stroke: Stroke) : WhiteboardMessage
        data object ClearCanvas : WhiteboardMessage
    }

    private class MockSession(override val id: SessionId) : TransportSession {
        val sent = mutableListOf<ByteArray>()
        val inbox = Channel<ByteArray>(Channel.UNLIMITED)

        override suspend fun send(channel: DeliveryChannel, packet: ByteArray) {
            sent += packet
        }

        override fun incoming(): ReceiveChannel<ByteArray> = inbox
        override suspend fun close() {
            inbox.close()
        }
    }

    private object WhiteboardCodec : MessageCodec<WhiteboardMessage> {
        override fun decode(bytes: ByteArray): WhiteboardMessage? {
            val str = bytes.decodeToString()
            val parts = str.split(";")
            return when (parts[0]) {
                "auth" -> WhiteboardMessage.Auth(parts[1])
                "draw" -> WhiteboardMessage.Draw(
                    Stroke(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat(), parts[4].toFloat(), parts[5].toInt()),
                )
                "clear" -> WhiteboardMessage.ClearCanvas
                else -> null
            }
        }

        override fun encode(message: WhiteboardMessage): ByteArray = when (message) {
            is WhiteboardMessage.Auth -> "auth;${message.user}".encodeToByteArray()
            is WhiteboardMessage.Draw -> "draw;${message.stroke.x1};${message.stroke.y1};${message.stroke.x2};${message.stroke.y2};${message.stroke.color}".encodeToByteArray()
            WhiteboardMessage.ClearCanvas -> "clear".encodeToByteArray()
        }
    }

    @Suppress("LongMethod")
    @Test
    fun multipleUsersCollaborateOnSharedWhiteboard() = runBlocking {
        val canvasHistory = mutableListOf<Stroke>()
        val activeUsers = mutableListOf<WhiteboardUser>()

        val drawingFeature = gameFeature<WhiteboardUser, WhiteboardMessage>("canvas") {
            onJoin { user ->
                activeUsers += user
                for (stroke in canvasHistory) {
                    runBlocking {
                        user.session.send(DeliveryChannel.Reliable, WhiteboardCodec.encode(WhiteboardMessage.Draw(stroke)))
                    }
                }
            }
            onLeave { user ->
                activeUsers.remove(user)
            }
            on<WhiteboardMessage.Draw> { author, draw ->
                canvasHistory += draw.stroke
                val packet = WhiteboardCodec.encode(draw)
                for (other in activeUsers) {
                    if (other.session.id != author.session.id) {
                        runBlocking { other.session.send(DeliveryChannel.Reliable, packet) }
                    }
                }
            }
            on<WhiteboardMessage.ClearCanvas> { _, _ ->
                canvasHistory.clear()
            }
        }

        val host = ServerHost(
            world = World(),
            codec = WhiteboardCodec,
            authenticator = { session, msg ->
                if (msg is WhiteboardMessage.Auth) {
                    AuthResult.Success(WhiteboardUser(session, msg.user))
                } else {
                    AuthResult.Denied("Must authenticate")
                }
            },
            features = Features<WhiteboardUser, WhiteboardMessage>().install(drawingFeature),
            config = ServerHostConfig(ticksPerSecond = 30),
        )

        val sessionA = MockSession(SessionId(1))
        val sessionB = MockSession(SessionId(2))

        val jobA = host.serve(sessionA)
        val jobB = host.serve(sessionB)

        // User A connects and draws a stroke
        sessionA.inbox.send("auth;UserA".encodeToByteArray())
        delay(20)
        host.step(0.033f)
        assertEquals(1, host.onlineCount)

        val stroke1 = Stroke(0f, 0f, 100f, 100f, 0xFF0000)
        sessionA.inbox.send(WhiteboardCodec.encode(WhiteboardMessage.Draw(stroke1)))
        delay(20)
        host.step(0.033f)
        assertEquals(1, canvasHistory.size)

        // User B connects; receives stroke1 from history
        sessionB.inbox.send("auth;UserB".encodeToByteArray())
        delay(20)
        host.step(0.033f)
        assertEquals(2, host.onlineCount)
        assertEquals(1, sessionB.sent.size)
        assertEquals(WhiteboardMessage.Draw(stroke1), WhiteboardCodec.decode(sessionB.sent[0]))

        // User B draws stroke2; User A receives stroke2 broadcast
        val stroke2 = Stroke(50f, 50f, 200f, 200f, 0x00FF00)
        sessionB.inbox.send(WhiteboardCodec.encode(WhiteboardMessage.Draw(stroke2)))
        delay(20)
        host.step(0.033f)
        assertEquals(2, canvasHistory.size)
        assertEquals(1, sessionA.sent.size)
        assertEquals(WhiteboardMessage.Draw(stroke2), WhiteboardCodec.decode(sessionA.sent[0]))

        sessionA.close()
        sessionB.close()
        jobA.join()
        jobB.join()
        host.step(0.033f)
        assertEquals(0, host.onlineCount)

        host.close()
    }
}
