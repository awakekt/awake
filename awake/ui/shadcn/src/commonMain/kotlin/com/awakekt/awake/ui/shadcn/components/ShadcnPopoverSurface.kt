/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.ui.shadcn.ShadcnThemeValues

/**
 * The floating surface `popover`, `dropdown-menu` and `select`'s content all share.
 *
 * Upstream spells it identically in all three: `rounded-md border bg-popover text-popover-foreground
 * shadow-md`. Only the inset differs -- `p-4`, `p-1`, and none.
 *
 * **The inset folds the border width in, and that is a fix rather than a detail.** CSS is
 * `border-box`, so a `p-1` surface with a `border` insets its content by 5px, not 4. Awake's border
 * is paint-only and Compose-faithful in that -- `Modifier.border` is a draw modifier, which is why
 * Compose chains `.border(...).padding(...)` -- so the recipe adds the width itself. Getting this
 * wrong is what put `shadcnDropdownMenu`'s items at x=4 width=152 inside a 160px surface where the
 * reference says x=5 width=150, and made the surface measure 136 tall against 138. One pixel a side
 * on both axes, invisible by eye, caught only by the parity report.
 *
 * `shadow-md` is not carried: there is no shadow primitive. A floating surface without one reads
 * flatter than upstream against a busy background, which is a real difference and listed as such.
 */
internal fun ShadcnThemeValues.popoverSurfaceStyle(inset: Dp): Style = Style {
    background(palette.popover)
    border(POPOVER_BORDER_WIDTH, palette.border)
    cornerRadius(radii.md)
    textColor(palette.popoverForeground)
    // border-box: the border consumes layout space in CSS, so the content inset is padding + border.
    contentPadding(inset + POPOVER_BORDER_WIDTH)
}

/** Tailwind's bare `border` is 1px. */
internal val POPOVER_BORDER_WIDTH: Dp = 1.dp

/** `min-w-[8rem]` on dropdown and select content. */
internal val POPOVER_MIN_WIDTH: Dp = 128.dp
