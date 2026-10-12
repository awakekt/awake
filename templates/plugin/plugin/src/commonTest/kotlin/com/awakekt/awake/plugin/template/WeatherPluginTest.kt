/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.plugin.template

import com.awakekt.awake.ecs.World
import com.awakekt.awake.editor.core.plugin.EditorProviderKind
import com.awakekt.awake.editor.core.plugin.KeybindingProvider
import com.awakekt.awake.editor.core.plugin.PluginRegistry
import com.awakekt.awake.editor.core.plugin.ProviderRegistry
import com.awakekt.awake.scene.core.Name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeatherPluginTest {
    @Test
    fun installsIntoAnyContractHostUnderItsOwnKinds() {
        val providers = ProviderRegistry()

        PluginRegistry(providers).install(WeatherPlugin())

        assertEquals(
            listOf(
                EditorProviderKind.BottomPanel,
                EditorProviderKind.Toolbar,
                EditorProviderKind.Keybinding,
                EditorProviderKind.EntityTemplate,
            ),
            providers.all.map { it.kind },
        )
        assertTrue(providers.all.all { it.metadata.id.value.startsWith(WeatherPlugin.PLUGIN_ID) })
    }

    @Test
    fun theKeybindingTogglesTheRainAndTheTemplateAddsCoreComponents() {
        val plugin = WeatherPlugin()
        val keys = plugin.createProviders().filterIsInstance<KeybindingProvider>().single()
        val world = World()
        val entity = world.create()

        keys.bindings.single().execute()
        RainCloudTemplate().template.configure(world, entity)

        assertTrue(plugin.weather.isRaining)
        assertEquals("Rain cloud", world.get<Name>(entity)?.value)
    }
}
