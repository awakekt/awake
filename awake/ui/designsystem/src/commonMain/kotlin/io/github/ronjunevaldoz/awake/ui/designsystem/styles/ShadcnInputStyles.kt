// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.designsystem.styles

import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.core.math2d.dp
import io.github.ronjunevaldoz.awake.ui.api.layout.UiInsets
import io.github.ronjunevaldoz.awake.ui.api.theme.UiThemeValues
import io.github.ronjunevaldoz.awake.ui.designsystem.ShadcnThemeValues
import io.github.ronjunevaldoz.awake.ui.designsystem.theme.ShadcnMetrics
import io.github.ronjunevaldoz.awake.ui.style.Style

/**
 * Canonical focus-ring fragment (`focus-visible:border-ring`) -- any interactive recipe composes
 * this via `then` to get the same focused-state border color, instead of duplicating its own
 * `focused {}` rule or hardcoding the ring token past the `Style` channel in headless. State
 * rules always outrank a later unconditional (see `StyleResolutionOrderTest`), so this can be
 * composed either before or after a recipe's own base style.
 */
internal fun UiThemeValues.shadcnFocusRing(): Style = Style {
    focused { borderColor(colors.ring) }
}

fun UiThemeValues.shadcnTextFieldStyle(variant: ShadcnTextFieldVariant, metrics: ShadcnMetrics): Style =
    shadcnInputStyle(variant, UiInsets(metrics.fieldPaddingX, metrics.inputPaddingY)) then shadcnFocusRing()

fun UiThemeValues.shadcnTextareaStyle(variant: ShadcnTextFieldVariant, metrics: ShadcnMetrics): Style =
    shadcnInputStyle(variant, UiInsets(metrics.fieldPaddingX, metrics.fieldPaddingY)) then shadcnFocusRing()

internal fun shadcnSelectOptionStyle(values: ShadcnThemeValues): Style = Style {
    background(values.colors.popover)
    foreground(values.colors.popoverForeground)
    contentPadding(horizontal = 8f.dp, vertical = 6f.dp)
    textSize(values.typography.label)
    // Same `sm` the selected row uses. shadcn's SelectItem is `rounded-sm` and its highlight is
    // a focus fill on that same rounded element (select.tsx: `rounded-sm ... focus:bg-accent`),
    // so the shape has to sit here on the base style, not only on the selected variant --
    // without it the hover fill painted square corners under a rounded selected row.
    shape(values.shapes.sm)
    hovered {
        background(values.colors.accent)
        foreground(values.colors.accentForeground)
    }
    active {
        background(values.colors.accent)
        foreground(values.colors.accentForeground)
    }
    disabled { foreground(values.colors.mutedForeground) }
}

// `sm`, matching shadcnDropdownItemStyle: a selected option is a menu row, and both shadcn's
// SelectItem and DropdownMenuItem are `rounded-sm`. At `md` the highlight read as a rounded
// pill sitting in the list -- a button, not a selected row.
internal fun shadcnSelectedOptionStyle(values: ShadcnThemeValues): Style = Style {
    background(values.colors.accent)
    foreground(values.colors.accentForeground)
    shape(values.shapes.sm)
}

internal fun shadcnSliderStyle(values: ShadcnThemeValues): Style = Style {
    background(values.colors.muted)
    foreground(values.colors.primary)
    shape(values.shapes.full)
    // slider()/rangeSlider() paint the track with this resolved border directly (the knob has
    // its own hardcoded 1dp fallback, but the track does not) -- without these, the track's
    // border and hover/active feedback silently disappeared when ui-headless's now-deleted
    // `theme.components.slider` ambient default (which used to supply them) was removed.
    border(1f.dp, values.colors.input)
    hovered { background(values.colors.muted) }
    active { borderColor(values.colors.accent) }
}

internal fun shadcnInputGroupStyle(values: ShadcnThemeValues): Style = Style {
    background(values.colors.background)
    border(1f.dp, values.colors.input)
    shape(values.shapes.md)
}

internal fun shadcnInputGroupAffixStyle(values: ShadcnThemeValues): Style = Style {
    foreground(values.colors.mutedForeground)
}

internal fun shadcnInputGroupAffixTextStyle(values: ShadcnThemeValues): Style = Style {
    textSize(values.typography.label)
}

internal fun shadcnInputOtpSlotStyle(values: ShadcnThemeValues, enabled: Boolean, isError: Boolean): Style = Style {
    background(values.colors.card)
    foreground(if (enabled) values.colors.foreground else values.colors.mutedForeground)
    border(1f.dp, if (isError) values.colors.destructive else values.colors.input)
    shape(values.shapes.md)
}

private fun UiThemeValues.shadcnInputStyle(variant: ShadcnTextFieldVariant, padding: UiInsets): Style = Style {
    when (variant) {
        ShadcnTextFieldVariant.Default -> {
            background(colors.background)
            foreground(colors.foreground)
            border(1f.dp, colors.input)
        }
        ShadcnTextFieldVariant.Filled -> {
            background(colors.muted)
            foreground(colors.foreground)
        }
        ShadcnTextFieldVariant.Ghost -> {
            background(Color.Transparent)
            foreground(colors.foreground)
        }
    }
    shape(shapes.md)
    contentPadding(padding.start, padding.top, padding.end, padding.bottom)
    textSize(typography.label)
    if (variant != ShadcnTextFieldVariant.Ghost) {
        hovered { background(colors.card) }
        active { background(colors.card) }
    }
    disabled { foreground(colors.mutedForeground) }
}
