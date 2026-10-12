/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.plugin.template

import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.editor.core.plugin.EditorPlugin
import com.awakekt.awake.editor.core.plugin.EditorProvider
import com.awakekt.awake.editor.core.plugin.PluginApi
import com.awakekt.awake.editor.core.plugin.PluginId
import com.awakekt.awake.editor.core.plugin.PluginMetadata

/**
 * The plugin's entry point, named by `entrypointClass` in plugin.json. A host calls
 * [createProviders] once on install and disposes the providers in reverse order on uninstall.
 */
class WeatherPlugin : EditorPlugin {
    override val metadata = PluginMetadata(
        id = PluginId(PLUGIN_ID),
        displayName = "Weather",
        version = "0.1.0",
        requiredApiVersion = PluginApi.currentVersion,
    )

    /** Shared by every provider below; a real plugin would edit scene data the game reads instead. */
    val weather = WeatherState()

    override fun createProviders(): List<EditorProvider> = listOf(
        WeatherPanel(weather),
        RainToggle(weather),
        RainKeys(weather),
        RainCloudTemplate(),
    )

    companion object {
        /** Prefix every provider ID with the plugin ID: provider IDs are global in a host. */
        const val PLUGIN_ID = "com.awakekt.awake.plugin.template"
    }
}

/** Whether it is raining in this editor session. Compose state, so panels redraw when it changes. */
class WeatherState {
    private val raining = mutableStateOf(false)

    val isRaining: Boolean get() = raining.value

    fun toggle() {
        raining.value = !raining.value
    }
}
