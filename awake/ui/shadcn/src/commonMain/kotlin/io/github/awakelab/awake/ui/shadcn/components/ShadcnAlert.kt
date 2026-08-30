/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.style.Style
import io.github.awakelab.awake.compose.foundation.style.StyleState
import io.github.awakelab.awake.compose.foundation.style.styleable
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.ShadcnThemeValues
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's alert: `rounded-lg border px-4 py-3 text-sm`, with a title and an optional description.
 *
 * The description is `text-destructive/90` in the destructive variant -- slightly softer than the
 * title, so the two read as a hierarchy rather than one block of red.
 *
 * Upstream lays this out as a grid so an optional leading icon gets its own column. This is a
 * Column with no icon slot yet; the grid arrives with the icon, not before it, because a
 * single-column grid is just a column.
 */
context(_: Composer)
fun shadcnAlert(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    variant: ShadcnAlertVariant = ShadcnAlertVariant.Default,
) {
    val theme = shadcnTheme
    val style = remember(theme) { theme.alertStyle() }
    val foreground = when (variant) {
        ShadcnAlertVariant.Default -> theme.palette.cardForeground
        ShadcnAlertVariant.Destructive -> theme.palette.destructive
    }

    Box(modifier.fillMaxWidth().styleable(StyleState.Default, style)) {
        // `grid gap-y-0.5` -- 2px between the title and the description, which is the last 2px of
        // the reference's 88px height.
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s0_5)) {
            ShadcnText(title, variant = ShadcnTextVariant.Small, color = foreground)
            if (description != null) {
                ShadcnText(
                    description,
                    variant = ShadcnTextVariant.Small,
                    color = when (variant) {
                        ShadcnAlertVariant.Default -> theme.palette.mutedForeground
                        // `*:data-[slot=alert-description]:text-destructive/90`
                        ShadcnAlertVariant.Destructive ->
                            theme.palette.destructive.withAlpha(DESCRIPTION_ALPHA)
                    },
                )
            }
        }
    }
}

/** The box, which is identical in both variants -- only the type colour differs. */
private fun ShadcnThemeValues.alertStyle(): Style = Style {
    background(palette.card)
    border(AlertBorderWidth, palette.border)
    cornerRadius(radii.lg)
    // `px-4 py-3`, with the border folded in -- shadcn is `border-box` and Awake's border reserves
    // no layout space, so without it the content box is 2px wide in each axis.
    contentPadding(
        horizontal = Tw.Spacing.s4 + AlertBorderWidth,
        vertical = Tw.Spacing.s3 + AlertBorderWidth,
    )
}

/** `text-destructive/90` on the description. */
private const val DESCRIPTION_ALPHA = 0.9f

/** Tailwind's bare `border` is 1px; the width scale is not generated -- see `ShadcnCard`. */
private val AlertBorderWidth: Dp = 1.dp

context(_: Composer)
fun ShadcnAlert(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    variant: ShadcnAlertVariant = ShadcnAlertVariant.Default,
) = shadcnAlert(title, modifier, description, variant)
