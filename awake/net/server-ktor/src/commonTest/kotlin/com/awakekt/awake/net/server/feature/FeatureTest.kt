/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.feature

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureTest {

    private class TestPlayer(val id: String)

    private sealed interface TestMessage {
        data class Ping(val seq: Int) : TestMessage
        data class Chat(val text: String) : TestMessage
        data class Unhandled(val value: Int) : TestMessage
    }

    @Test
    fun featureRegistersHandlersAndDispatchesMessage() {
        var receivedSeq = -1
        val feature = gameFeature<TestPlayer, TestMessage>("ping") {
            on<TestMessage.Ping> { _, ping ->
                receivedSeq = ping.seq
            }
        }

        val player = TestPlayer("p1")
        val router = feature.router()

        assertTrue(router.handles(TestMessage.Ping(42)))
        assertFalse(router.handles(TestMessage.Chat("hi")))

        val handled = router.route(player, TestMessage.Ping(42))
        assertTrue(handled)
        assertEquals(42, receivedSeq)
    }

    @Test
    fun duplicateHandlerWithinSameFeatureThrows() {
        assertFailsWith<IllegalArgumentException> {
            gameFeature<TestPlayer, TestMessage>("dup") {
                on<TestMessage.Ping> { _, _ -> }
                on<TestMessage.Ping> { _, _ -> }
            }
        }
    }

    @Test
    fun duplicateHandlerAcrossDifferentFeaturesThrowsOnInstall() {
        val f1 = gameFeature<TestPlayer, TestMessage>("f1") {
            on<TestMessage.Ping> { _, _ -> }
        }
        val f2 = gameFeature<TestPlayer, TestMessage>("f2") {
            on<TestMessage.Ping> { _, _ -> }
        }

        assertFailsWith<IllegalArgumentException> {
            Features<TestPlayer, TestMessage>().install(f1, f2)
        }
    }

    @Test
    fun featuresLifecycleDispatchesInOrder() {
        val events = mutableListOf<String>()

        val f1 = gameFeature<TestPlayer, TestMessage>("f1") {
            onJoin { events += "f1.join:${it.id}" }
            onLeave { events += "f1.leave:${it.id}" }
            onTick { events += "f1.tick:$it" }
            ownState { events += "f1.ownState:${it.id}" }
        }
        val f2 = gameFeature<TestPlayer, TestMessage>("f2") {
            onJoin { events += "f2.join:${it.id}" }
            onLeave { events += "f2.leave:${it.id}" }
            onTick { events += "f2.tick:$it" }
            ownState { events += "f2.ownState:${it.id}" }
        }

        val features = Features<TestPlayer, TestMessage>().install(f1, f2)
        val player = TestPlayer("p1")

        features.joined(player)
        features.ticked(0.033f)
        features.ownState(player)
        features.left(player)

        assertEquals(
            listOf(
                "f1.join:p1",
                "f2.join:p1",
                "f1.tick:0.033",
                "f2.tick:0.033",
                "f1.ownState:p1",
                "f2.ownState:p1",
                "f1.leave:p1",
                "f2.leave:p1",
            ),
            events,
        )
    }
}
