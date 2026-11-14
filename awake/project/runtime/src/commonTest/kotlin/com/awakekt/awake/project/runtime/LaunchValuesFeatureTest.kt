/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.config.LaunchOptions
import com.awakekt.awake.core.config.LaunchValues
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneDocument
import kotlin.test.Test
import kotlin.test.assertEquals

/** Launch options reach a feature as a host service: parsed before the engine starts, read once at registration. */
class LaunchValuesFeatureTest {
    object Launch : LaunchOptions() {
        val server = string("server", "ws://localhost:7000", "Game server address")
    }

    private class NetSystem(val server: String) : System {
        override fun update(world: World, delta: Float) = Unit
    }

    private class NetFeature(private val onBuilt: (NetSystem) -> Unit) : SceneFeature {
        override val id = "test.net"

        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
            plan.frame("net") { host -> NetSystem(host.require<LaunchValues>()[Launch.server]).also(onBuilt) }
        }
    }

    @Test
    fun aFeatureReadsALaunchOptionThroughItsHost() {
        val launch = Launch.parse(mapOf("server" to listOf("wss://play.example")))
        val host = FeatureHost.headless({ GameplayInput(Input().currentSnapshot, InputOwnership()) }).provide(launch)
        var built: NetSystem? = null
        sceneSystemsFor(SceneDocument(), host, listOf(NetFeature { built = it })).use {
            assertEquals("wss://play.example", built?.server)
        }
    }
}
