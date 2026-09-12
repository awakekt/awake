/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.foundation.style.disabled
import com.awakekt.awake.compose.foundation.style.focused
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.ShadcnThemeValues

/**
 * The box `input` and `textarea` share.
 *
 * Their class strings are the same but for the height rule and the vertical pad -- `h-9 py-1` against
 * `min-h-16 py-2` -- so the shared part lives here rather than being typed twice and drifting.
 *
 * **`bg-transparent` in light mode, `dark:bg-input/30` in dark mode.** A field takes the colour of
 * whatever it sits on in light mode, while the dark reference gives it the low-alpha input tint.
 * Reaching for `palette.background` in both modes gives a field that punches a hole in a card.
 *
 * Focus is `border-ring` plus `ring-[3px] ring-ring/50`; the outer ring is applied by the field
 * component's `Modifier.focusRing` extension because it is not part of the style box model.
 */
internal fun ShadcnThemeValues.fieldStyle(
    verticalPadding: Dp,
    focusRingMode: ShadcnFocusRingMode = ShadcnFocusRingMode.DrawWithContent,
): Style = Style {
    background(
        if (config.dark) {
            palette.input.withAlpha(palette.input.a * DARK_INPUT_ALPHA)
        } else {
            com.awakekt.awake.core.color.Color.Transparent
        },
    )
    border(FIELD_BORDER_WIDTH, palette.input)
    cornerRadius(radii.md)
    // Border folded into the inset: shadcn is `border-box` and Awake's border reserves no layout
    // space, so without this a field's content box is 2px wider than the reference's in each axis.
    contentPadding(
        horizontal = Tw.Spacing.s3 + FIELD_BORDER_WIDTH,
        vertical = verticalPadding + FIELD_BORDER_WIDTH,
    )
    focused(
        Style {
            border(
                if (focusRingMode == ShadcnFocusRingMode.StyleOnly) FieldFocusRingWidth else FIELD_BORDER_WIDTH,
                palette.ring,
            )
            cornerRadius(radii.md)
        },
    )
    // `disabled:opacity-50`; upstream also sets `cursor-not-allowed`, which a canvas has no notion of.
    disabled(Style { alpha(DISABLED_ALPHA) })
}

/** Tailwind's bare `border` is 1px. */
private val FIELD_BORDER_WIDTH: Dp = 1.dp

private const val DISABLED_ALPHA = 0.5f

/** Source `dark:bg-input/30`, composed with the token's existing alpha. */
private const val DARK_INPUT_ALPHA = 0.3f
