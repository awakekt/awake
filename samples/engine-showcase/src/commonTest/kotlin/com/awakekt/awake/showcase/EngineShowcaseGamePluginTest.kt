/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.plugin.GamePluginRegistry
import com.awakekt.awake.scene.core.plugin.PluginId
import com.awakekt.awake.showcase.examples.ragdoll.RagdollGamePlugin
import com.awakekt.awake.showcase.examples.streaming.WorldStreamGamePlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineShowcaseGamePluginTest {

    @Test
    fun ragdollShowcasesDeclareRagdollGamePlugin() {
        val ragdoll = EngineShowcases.first { it.id == "ragdoll" }
        assertTrue(ragdoll.plugins.any { it is RagdollGamePlugin })

        val skinnedRagdoll = EngineShowcases.first { it.id == "skinned-ragdoll" }
        assertTrue(skinnedRagdoll.plugins.any { it is RagdollGamePlugin })
    }

    @Test
    fun streamedNavShowcaseDeclaresWorldStreamGamePlugin() {
        val streamedNav = EngineShowcases.first { it.id == "streamed-nav" }
        assertTrue(streamedNav.plugins.any { it is WorldStreamGamePlugin })
    }

    @Test
    fun gamePluginsInstallCleanlyIntoWorld() {
        val world = World()
        val ragdollPlugin = RagdollGamePlugin()
        val streamPlugin = WorldStreamGamePlugin()

        val registry = GamePluginRegistry()
        registry.install(ragdollPlugin, world)
        registry.install(streamPlugin, world)

        assertTrue(registry.isInstalled(PluginId("com.awakekt.pro.physics-ragdoll")))
        assertTrue(registry.isInstalled(PluginId("com.awakekt.pro.worldstream")))
        assertEquals(ragdollPlugin, registry.getPlugin(PluginId("com.awakekt.pro.physics-ragdoll")))
        assertEquals(streamPlugin, registry.getPlugin(PluginId("com.awakekt.pro.worldstream")))
    }
}
