/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.gettingstarted

import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage

internal val ThemingPage = ShowcasePage(
    id = "theming",
    title = "Theming",
    category = ShowcaseCategory.GettingStarted,
    description = "Live preset, base color, accent, and dark mode theme controls.",
    usageCode = "val theme = shadcnThemeValues(preset = ShadcnStylePreset.Vega, dark = true)",
    previewWidth = 920,
    previewHeight = 640,
    notes = listOf("Re-themes content pane live while maintaining shell chrome."),
    hero = { state -> ShowcaseControlsPreview(state) },
)
