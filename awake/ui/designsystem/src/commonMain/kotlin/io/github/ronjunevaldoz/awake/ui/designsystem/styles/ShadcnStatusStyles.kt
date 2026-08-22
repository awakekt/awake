// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.designsystem.styles

import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.core.math2d.dp
import io.github.ronjunevaldoz.awake.core.math2d.sp
import io.github.ronjunevaldoz.awake.ui.api.theme.UiThemeValues
import io.github.ronjunevaldoz.awake.ui.designsystem.ShadcnThemeValues
import io.github.ronjunevaldoz.awake.ui.style.Style

internal fun shadcnProgressStyle(values: UiThemeValues): Style = Style {
    background(values.colors.primary.withAlpha(0.2f))
    foreground(values.colors.primary)
    border(0f.dp, Color.Transparent)
    shape(values.shapes.full)
}

internal fun shadcnSkeletonStyle(values: UiThemeValues): Style = Style {
    background(values.colors.muted)
    shape(values.shapes.md)
}

internal fun shadcnSpinnerStyle(values: UiThemeValues): Style = Style {
    foreground(values.colors.primary)
}

// 10sp has no matching UiTypography step (12/14/16/20/24/30) -- left as a literal per F8.
internal fun shadcnKbdStyle(values: ShadcnThemeValues): Style = Style {
    background(values.colors.muted)
    foreground(values.colors.mutedForeground)
    border(1f.dp, values.colors.border)
    shape(values.shapes.xs)
    contentPadding(6f.dp, 0f.dp, 6f.dp, 0f.dp)
    textSize(10f.sp)
}

// Pinned alert.tsx (`shadcn-6261bd8-new-york-v4`): base is `rounded-lg border p-4 text-sm`, with
//   default     -> `bg-background text-foreground`
//   destructive -> `border-destructive/50 text-destructive`   (sets NO background)
// The captured computed styles agree exactly: default is `oklch(1 0 0)` on a gray 1px border,
// destructive is `rgba(0, 0, 0, 0)` -- fully transparent -- behind a border at 0.5 alpha.
// This previously painted `muted` for default and `destructive/10` for destructive with a
// full-strength border, which is a different (older) alert convention than the pinned one.
internal fun shadcnAlertStyle(values: ShadcnThemeValues, variant: ShadcnAlertVariant): Style = Style {
    val destructive = variant == ShadcnAlertVariant.Destructive
    background(if (destructive) Color.Transparent else values.colors.background)
    foreground(if (destructive) values.colors.destructive else values.colors.foreground)
    border(1f.dp, if (destructive) values.colors.destructive.withAlpha(0.5f) else values.colors.border)
    shape(values.shapes.lg)
    contentPadding(16f.dp)
}

internal fun shadcnStatusEmptyStyle(): Style = Style { contentPadding(24f.dp) }
