/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.feature

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class FeatureHooksTest {
    private class TestPlayer(val id: String)

    private class Roster : GameFeature<TestPlayer, String> {
        override val id: String = "roster"
        override fun router(): MessageRouter<TestPlayer, String> = MessageRouter.empty()
    }

    private class Unused : GameFeature<TestPlayer, String> {
        override val id: String = "unused"
        override fun router(): MessageRouter<TestPlayer, String> = MessageRouter.empty()
    }

    @Test
    fun beforeSnapshotDispatchesInRegistrationOrderAndDefaultsToNoOp() {
        val events = mutableListOf<String>()
        val f1 = gameFeature<TestPlayer, String>("f1") { beforeSnapshot { events += "f1" } }
        val f2 = gameFeature<TestPlayer, String>("f2") { beforeSnapshot { events += "f2" } }
        val features = Features<TestPlayer, String>().install(f1, Roster(), f2)

        features.beforeSnapshot()

        assertEquals(listOf("f1", "f2"), events)
    }

    @Test
    fun builderRunsEveryJoinAndLeaveHandlerInRegistrationOrder() {
        val events = mutableListOf<String>()
        val feature = gameFeature<TestPlayer, String>("multi") {
            onJoin { events += "join1:${it.id}" }
            onLeave { events += "leave1:${it.id}" }
            onJoin { events += "join2:${it.id}" }
            onLeave { events += "leave2:${it.id}" }
            onJoin { events += "join3:${it.id}" }
        }
        val player = TestPlayer("p1")

        feature.onJoin(player)
        feature.onLeave(player)

        assertEquals(listOf("join1:p1", "join2:p1", "join3:p1", "leave1:p1", "leave2:p1"), events)
    }

    @Test
    fun featureOfReturnsTheInstalledFeatureOfThatTypeOrNull() {
        val roster = Roster()
        val dsl = gameFeature<TestPlayer, String>("dsl") { }
        val features = Features<TestPlayer, String>().install(dsl, roster)

        assertSame(roster, features.featureOf(Roster::class))
        assertSame(roster, features.featureOf<Roster>())
        assertNull(features.featureOf<Unused>())
        assertNull(Features<TestPlayer, String>().featureOf<Roster>())
    }
}
