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

class ServerHostBeforeSnapshotTest {
    private data class DummyPlayer(override val session: TransportSession) : HostPlayer

    private object NoopCodec : MessageCodec<String> {
        override fun decode(bytes: ByteArray): String = bytes.decodeToString()
        override fun encode(message: String): ByteArray = message.encodeToByteArray()
    }

    @Test
    fun eachTickRunsBeforeSnapshotAfterEveryOnTick() {
        val events = mutableListOf<String>()
        val f1 = gameFeature<DummyPlayer, String>("f1") {
            beforeSnapshot { events += "f1.beforeSnapshot" }
            onTick { events += "f1.tick" }
        }
        val f2 = gameFeature<DummyPlayer, String>("f2") {
            onTick { events += "f2.tick" }
            beforeSnapshot { events += "f2.beforeSnapshot" }
        }
        val host = ServerHost(
            world = World(),
            codec = NoopCodec,
            authenticator = { session, _ -> AuthResult.Success(DummyPlayer(session)) },
            features = Features<DummyPlayer, String>().install(f1, f2),
        )

        host.step(0.05f)
        host.step(0.05f)

        val oneTick = listOf("f1.tick", "f2.tick", "f1.beforeSnapshot", "f2.beforeSnapshot")
        assertEquals(oneTick + oneTick, events)
    }
}
