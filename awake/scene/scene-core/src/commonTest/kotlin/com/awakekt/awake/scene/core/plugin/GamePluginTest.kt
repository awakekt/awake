/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.plugin

import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GamePluginTest {

    private class TestGamePlugin(
        override val metadata: PluginMetadata = PluginMetadata(
            id = PluginId("test.plugin"),
            displayName = "Test Plugin",
            version = "1.0.0",
            description = "A test game plugin",
        ),
    ) : GamePlugin {
        var installedWorld: World? = null

        override fun install(world: World) {
            installedWorld = world
        }
    }

    @Test
    fun registryInstallsPluginAndInvokesInstallCallback() {
        val registry = GamePluginRegistry()
        val plugin = TestGamePlugin()
        val world = World()

        assertFalse(registry.isInstalled(PluginId("test.plugin")))

        registry.install(plugin, world)

        assertTrue(registry.isInstalled(PluginId("test.plugin")))
        assertEquals(plugin, registry.getPlugin(PluginId("test.plugin")))
        assertEquals(world, plugin.installedWorld)
        assertEquals(1, registry.installed.size)
    }

    @Test
    fun registryRejectsDuplicatePluginId() {
        val registry = GamePluginRegistry()
        val plugin1 = TestGamePlugin()
        val plugin2 = TestGamePlugin()

        registry.install(plugin1)

        val ex = assertFailsWith<IllegalArgumentException> {
            registry.install(plugin2)
        }
        assertTrue(ex.message!!.contains("is already installed"))
    }

    @Test
    fun installAllInstallsMultiplePluginsInOrder() {
        val registry = GamePluginRegistry()
        val pluginA = TestGamePlugin(PluginMetadata(PluginId("a"), "A", "1.0"))
        val pluginB = TestGamePlugin(PluginMetadata(PluginId("b"), "B", "1.0"))

        registry.installAll(listOf(pluginA, pluginB))

        assertEquals(listOf(pluginA, pluginB), registry.installed)
    }
}
