/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.style.Style
import io.github.awakelab.awake.compose.foundation.style.StyleState
import io.github.awakelab.awake.compose.foundation.style.styleable
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.ShadcnThemeValues
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's badge: a pill with a label.
 *
 * Upstream `badge.tsx`'s base is `rounded-full border border-transparent px-2 py-0.5 text-xs
 * font-medium`, and the border being *transparent by default* is the part worth keeping: every
 * variant then occupies the same box, so `Outline` colouring its border does not resize it.
 *
 * **Destructive is `text-white`, literally.** Not `text-destructive-foreground`, which shadcn does
 * not define. This is the third place upstream hardcodes white where `shadcn-compose` substitutes a
 * semantic token -- the slider's thumb (`bg-white` vs `colors.background`) and this one -- so it is
 * worth treating as a pattern when porting: grep upstream for `-white` before trusting a token.
 */
context(_: Composer)
fun ShadcnBadge(
    label: String,
    modifier: Modifier = Modifier,
    variant: ShadcnBadgeVariant = ShadcnBadgeVariant.Default,
) {
    val theme = shadcnTheme
    val style = remember(theme, variant) { theme.badgeStyle(variant) }
    Box(modifier.styleable(StyleState.Default, style)) {
        ShadcnText(label, variant = ShadcnTextVariant.Xs, color = theme.badgeForeground(variant))
    }
}

/**
 * The badge's box, per variant.
 *
 * Every variant carries the same 1px border so the box never changes size between them -- upstream
 * spells that `border border-transparent` on the base and only recolours it in `Outline`.
 */
private fun ShadcnThemeValues.badgeStyle(variant: ShadcnBadgeVariant): Style = Style {
    // `rounded-full px-2 py-0.5`, per axis now that StyleScope carries both. The border is folded
    // in because shadcn is `border-box` and Awake's border reserves no layout space -- badge carries
    // one on every variant, so without this every badge is 2px short in both axes.
    cornerRadius(radii.full)
    contentPadding(
        horizontal = Tw.Spacing.s2 + BadgeBorderWidth,
        vertical = Tw.Spacing.s0_5 + BadgeBorderWidth,
    )
    border(
        BadgeBorderWidth,
        if (variant == ShadcnBadgeVariant.Outline) palette.border else Color.Transparent,
    )
    background(
        when (variant) {
            ShadcnBadgeVariant.Default -> palette.primary
            ShadcnBadgeVariant.Secondary -> palette.secondary
            ShadcnBadgeVariant.Destructive -> palette.destructive
            ShadcnBadgeVariant.Outline, ShadcnBadgeVariant.Ghost, ShadcnBadgeVariant.Link ->
                Color.Transparent
        },
    )
}

/** The label colour, which does not always follow the fill -- see [ShadcnBadge] on `text-white`. */
private fun ShadcnThemeValues.badgeForeground(variant: ShadcnBadgeVariant): Color = when (variant) {
    ShadcnBadgeVariant.Default -> palette.primaryForeground
    ShadcnBadgeVariant.Secondary -> palette.secondaryForeground
    ShadcnBadgeVariant.Destructive -> Color.White
    ShadcnBadgeVariant.Outline, ShadcnBadgeVariant.Ghost -> palette.foreground
    ShadcnBadgeVariant.Link -> palette.primary
}

/** Tailwind's bare `border` is 1px; the width scale is not generated -- see `ShadcnCard`. */
private val BadgeBorderWidth = io.github.awakelab.awake.compose.ui.unit.Dp(1f)
