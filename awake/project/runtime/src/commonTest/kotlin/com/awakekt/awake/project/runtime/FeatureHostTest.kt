/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A feature gets the host's own services once, when it registers, and its system gets them by constructor. */
class FeatureHostTest {
    private class NetLink(val name: String)

    private class NetSystem(val link: NetLink) : System {
        var updates = 0

        override fun update(world: World, delta: Float) {
            updates++
        }
    }

    private class NetFeature(private val onBuilt: (NetSystem) -> Unit = {}) : SceneFeature {
        override val id = "test.net"

        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
            plan.frame("net") { host -> NetSystem(host.require<NetLink>()).also(onBuilt) }
        }
    }

    private val gameplay = { GameplayInput(Input().currentSnapshot, InputOwnership()) }

    private fun host() = FeatureHost.headless(gameplay)

    @Test
    fun aProvidedServiceReachesTheSystemsConstructor() {
        val link = NetLink("server")
        var built: NetSystem? = null
        sceneSystemsFor(SceneDocument(), host().provide(link), listOf(NetFeature { built = it })).use { set ->
            assertSame(link, built?.link)
            assertTrue(set.planned.any { it.name == "net" })
            repeat(3) { set.frame.filterIsInstance<NetSystem>().single().update(World(), 0.016f) }
            assertEquals(3, built?.updates)
            assertSame(link, built?.link, "the system keeps what it was built with")
        }
    }

    @Test
    fun aMissingRequiredServiceFailsAtRegistrationNamingTheFeatureAndTheType() {
        val refusal = assertFailsWith<IllegalStateException> {
            sceneSystemsFor(SceneDocument(), host(), listOf(NetFeature()))
        }
        val message = refusal.message.orEmpty()
        assertTrue("test.net" in message, message)
        assertTrue("net" in message && "NetLink" in message, message)
    }

    @Test
    fun optionalIsNullWhenNothingWasProvidedAndProvideReplacesTheFirstValue() {
        assertNull(host().optional<NetLink>())
        val second = NetLink("second")
        assertSame(second, host().provide(NetLink("first")).provide(second).require<NetLink>())
    }

    @Test
    fun theOtherCopiesKeepWhatWasProvided() {
        val link = NetLink("kept")
        val copy = host().provide(link).withHostRole(com.awakekt.awake.scene.runtime.SceneHostRole.SimulationOnly)
        assertSame(link, copy.require<NetLink>())
    }
}
