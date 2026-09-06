/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.foundation.style.StyleScope
import com.awakekt.awake.compose.foundation.style.disabled
import com.awakekt.awake.compose.foundation.style.hovered
import com.awakekt.awake.compose.foundation.style.pressed
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.ShadcnThemeValues

/** Visual translation of pinned shadcn `button.tsx`; composition stays in [ShadcnButton.kt]. */
internal fun ShadcnThemeValues.shadcnButtonStyle(variant: ShadcnButtonVariant): Style = Style {
    cornerRadius(radii.md)
    when (variant) {
        ShadcnButtonVariant.Default -> fill(palette.primary, HOVER_ALPHA)
        ShadcnButtonVariant.Destructive -> fill(palette.destructive, HOVER_ALPHA)
        ShadcnButtonVariant.Secondary -> fill(palette.secondary, SECONDARY_HOVER_ALPHA)
        ShadcnButtonVariant.Outline -> {
            background(palette.background)
            border(ShadcnButtonBorderWidth, palette.border)
            hovered(Style { background(palette.accent) })
        }
        ShadcnButtonVariant.Ghost -> hovered(Style { background(palette.accent) })
        ShadcnButtonVariant.Link -> Unit
    }
    disabled(Style { alpha(DISABLED_ALPHA) })
}

internal fun ShadcnThemeValues.buttonForeground(variant: ShadcnButtonVariant): Color = when (variant) {
    ShadcnButtonVariant.Default -> palette.primaryForeground
    ShadcnButtonVariant.Destructive -> Color.White
    ShadcnButtonVariant.Secondary -> palette.secondaryForeground
    ShadcnButtonVariant.Outline, ShadcnButtonVariant.Ghost -> palette.foreground
    ShadcnButtonVariant.Link -> palette.primary
}

private fun StyleScope.fill(color: Color, hoverAlpha: Float) {
    background(color)
    hovered(Style { background(color.withAlpha(hoverAlpha)) })
    pressed(Style { background(color.withAlpha(PRESSED_ALPHA)) })
}

/** Tailwind's bare `border` is 1px; its generated scale has no border-width entry. */
internal val ShadcnButtonBorderWidth: Dp = 1.dp

/** Source Button's `has-[>svg]:px-3`. */
internal val ShadcnButtonIconPadding: Dp = Tw.Spacing.s3

/** Source Button's shared `gap-2`. */
internal val ShadcnButtonIconLabelGap: Dp = Tw.Spacing.s2

private const val HOVER_ALPHA = 0.9f
private const val SECONDARY_HOVER_ALPHA = 0.8f
private const val PRESSED_ALPHA = 0.75f
private const val DISABLED_ALPHA = 0.5f
