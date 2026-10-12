/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.plugin.template

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.editor.core.plugin.EditorProviderKind
import com.awakekt.awake.editor.core.plugin.PanelProvider
import com.awakekt.awake.editor.core.plugin.ProviderId
import com.awakekt.awake.editor.core.plugin.ProviderMetadata
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnText

/** A bottom-panel tab. The host owns the tab and labels it with the display name, "Weather". */
class WeatherPanel(private val weather: WeatherState) : PanelProvider {
    override val metadata = ProviderMetadata(ProviderId("${WeatherPlugin.PLUGIN_ID}.panel"), "Weather")
    override val kind = EditorProviderKind.BottomPanel

    context(_: Composer)
    override fun content() {
        Column {
            ShadcnText(if (weather.isRaining) "It is raining." else "Clear skies.")
            ShadcnButton(if (weather.isRaining) "Stop the rain" else "Make it rain", onClick = weather::toggle)
        }
    }
}
