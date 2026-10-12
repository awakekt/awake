/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.plugin.template

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.editor.core.plugin.ActionId
import com.awakekt.awake.editor.core.plugin.KeyChord
import com.awakekt.awake.editor.core.plugin.Keybinding
import com.awakekt.awake.editor.core.plugin.KeybindingProvider
import com.awakekt.awake.editor.core.plugin.ProviderId
import com.awakekt.awake.editor.core.plugin.ProviderMetadata
import com.awakekt.awake.editor.core.plugin.ToolbarProvider
import com.awakekt.awake.ui.shadcn.components.ShadcnButton

/** A toolbar button; the host places it and uses the display name as its tooltip. */
class RainToggle(private val weather: WeatherState) : ToolbarProvider {
    override val metadata = ProviderMetadata(ProviderId("${WeatherPlugin.PLUGIN_ID}.toolbar"), "Toggle rain")

    context(_: Composer)
    override fun content() {
        ShadcnButton(if (weather.isRaining) "Rain: on" else "Rain: off", onClick = weather::toggle)
    }
}

/** Ctrl+R toggles the rain. The host owns the keymap, so a user can rebind it. */
class RainKeys(private val weather: WeatherState) : KeybindingProvider {
    override val metadata = ProviderMetadata(ProviderId("${WeatherPlugin.PLUGIN_ID}.keys"), "Weather keys")
    override val bindings = listOf(
        Keybinding(
            id = ActionId("${WeatherPlugin.PLUGIN_ID}.toggle-rain"),
            displayName = "Toggle rain",
            defaultChords = listOf(KeyChord(Key.R, ctrl = true)),
            category = "Weather",
            execute = {
                weather.toggle()
                true
            },
        ),
    )
}
