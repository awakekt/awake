/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.ui.theme

import io.github.awakelab.awake.ui.shadcn.ShadcnAccent
import io.github.awakelab.awake.ui.shadcn.ShadcnBaseColor
import io.github.awakelab.awake.ui.shadcn.ShadcnStylePreset
import io.github.awakelab.awake.ui.shadcn.ShadcnThemeValues
import io.github.awakelab.awake.ui.shadcn.shadcnThemeValues

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
