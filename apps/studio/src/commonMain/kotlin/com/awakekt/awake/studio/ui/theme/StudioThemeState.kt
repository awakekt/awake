/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.ui.theme

import com.awakekt.awake.ui.shadcn.ShadcnAccent
import com.awakekt.awake.ui.shadcn.ShadcnBaseColor
import com.awakekt.awake.ui.shadcn.ShadcnStylePreset
import com.awakekt.awake.ui.shadcn.ShadcnThemeValues
import com.awakekt.awake.ui.shadcn.shadcnThemeValues

/**
 * Retained state holder for Awake Studio's live Shadcn theme settings.
 */
class StudioThemeState(
    var isDark: Boolean = true,
    var preset: ShadcnStylePreset = ShadcnStylePreset.Vega,
    var baseColor: ShadcnBaseColor = ShadcnBaseColor.Zinc,
    var accent: ShadcnAccent = ShadcnAccent.Base,
) {
    val themeValues: ShadcnThemeValues
        get() = shadcnThemeValues(
            preset = preset,
            baseColor = baseColor,
            accent = accent,
            dark = isDark,
        )
}
