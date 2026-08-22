// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.designsystem.styles

import io.github.ronjunevaldoz.awake.core.math2d.dp
import io.github.ronjunevaldoz.awake.ui.designsystem.ShadcnThemeValues
import io.github.ronjunevaldoz.awake.ui.designsystem.theme.ShadcnMetrics
import io.github.ronjunevaldoz.awake.ui.style.Style
import io.github.ronjunevaldoz.awake.ui.tailwind.Tw

// shadcn's DropdownMenuContent is `p-1` plus a `border`. Under CSS `border-box` that 1px border
// CONSUMES layout space, so its real content inset is border + padding = 5px, not 4px.
//
// Awake's border is paint-only and reserves no layout space -- `surfaceCore` insets content by
// `resolved.contentPadding` alone (`ui-core/layouts/Surface.kt`), never by `borderWidth`. That is
// deliberate and Compose-faithful: Compose's `Modifier.border` is a draw modifier, not a layout
// one, which is why Compose code chains `.border(...).padding(...)` to inset content. So the
// border width must be added here rather than "fixed" in ui-core, which would break that parity.
//
// `ShadcnBorderBoxInsetTest` pins the arithmetic.
private val DropdownBorderWidth = 1f.dp

internal fun shadcnDropdownSurfaceStyle(values: ShadcnThemeValues): Style = Style {
    background(values.colors.popover)
    foreground(values.colors.popoverForeground)
    border(DropdownBorderWidth, values.colors.border)
    shape(values.shapes.md)
    // `Tw.Spacing.s1` rather than a bare `4f.dp`: this value IS shadcn's `p-1`, and naming the
    // Tailwind step keeps that translation traceable instead of leaving a literal whose origin
    // has to be rediscovered. (Most sibling styles still use raw literals -- see the note in
    // docs/tasks/2026-08-21-modifier-layout-compose-parity-plan.md.)
    contentPadding(Tw.Spacing.s1 + DropdownBorderWidth)
}
internal fun shadcnDropdownItemStyle(values: ShadcnThemeValues, selected: Boolean, destructive: Boolean): Style = Style {
    background(if (selected) values.colors.accent else values.colors.popover)
    foreground(
        if (selected) {
            values.colors.accentForeground
        } else if (destructive) {
            values.colors.destructive
        } else {
            values.colors.popoverForeground
        },
    )
    shape(values.shapes.sm)
    contentPadding(horizontal = 8f.dp, vertical = 6f.dp)
    textSize(values.typography.label)
    hovered {
        background(if (destructive) values.colors.destructive.withAlpha(0.1f) else values.colors.accent)
        foreground(if (destructive) values.colors.destructive else values.colors.accentForeground)
    }
}
internal fun shadcnTooltipStyle(values: ShadcnThemeValues): Style = Style {
    background(values.colors.foreground)
    foreground(values.colors.background)
    shape(values.shapes.md)
    contentPadding(horizontal = 12f.dp, vertical = 6f.dp)
    textSize(values.typography.caption)
    lineHeight(values.typography.body)
}
internal fun shadcnDialogTitleStyle(values: ShadcnThemeValues): Style = Style { textSize(values.typography.title) }
internal fun shadcnDialogBodyStyle(values: ShadcnThemeValues): Style = Style { textSize(values.typography.body) }
