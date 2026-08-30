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
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.RowScope
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.style.Style
import io.github.awakelab.awake.compose.foundation.style.StyleState
import io.github.awakelab.awake.compose.foundation.style.styleable
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.ShadcnThemeValues
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's card: a bordered, filled panel.
 *
 * Upstream `card.tsx` is `flex flex-col gap-6 rounded-xl border bg-card py-6 text-card-foreground
 * shadow-sm`, and comparing that against what this repo had turned up one drift worth naming:
 * **upstream is `rounded-xl` and the old recipe used the `lg` step.** In shadcn v4 `--radius-lg` is
 * the base radius while `--radius-xl` is base + 4px, so the card was 4px squarer than shadcn's at
 * every radius setting -- invisible beside a mockup, and exactly what a parity capture catches.
 *
 * **There is no `shadcnSurface` here on purpose.** `surface` is not a shadcn component; the registry
 * has `card`, `popover`, `dialog` and `alert`. The old one existed only because `ui-core`'s
 * `surface()` was the sole way to draw a filled rounded bordered box, and `:compose:foundation` does
 * that with `Modifier.background`/`.border` -- which is also how Compose does it, since `Surface` is
 * Material's and foundation has none. A generic container is a `Box` with those modifiers; this is
 * the shadcn-styled one.
 *
 * Not carried over: `shadow-sm` needs a shadow primitive this engine does not have.
 *
 * `gap-6` between children is applied here, so composing [ShadcnCardHeader]/[ShadcnCardContent]/
 * [ShadcnCardFooter] inside `content` gets upstream's spacing between them. The container's own
 * horizontal inset (`contentPadding` applied on both axes, see [cardStyle]) is a stopgap for
 * callers still using the raw `content` slot directly rather than the new sub-slots -- it is not
 * upstream's shape, where the container itself carries no horizontal padding at all.
 */
context(_: Composer)
fun ShadcnCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = Tw.Spacing.s6,
    content: (
    context(Composer)
        () -> Unit
    )? = null,
) {
    val theme = shadcnTheme
    val style = remember(theme, contentPadding) { theme.cardStyle(contentPadding) }
    Box(modifier.styleable(StyleState.Default, style)) {
        if (content != null) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s6),
            ) {
                content()
            }
        }
    }
}

/**
 * shadcn's `CardHeader`: the `px-6` inset upstream's container itself does not carry.
 *
 * Real shadcn positions a trailing [ShadcnCardHeader]-slot action in a CSS grid column
 * (`data-slot=card-action`, `col-start-2 row-span-2 justify-self-end`); this engine has no grid
 * primitive, so a trailing action is approximated with a [Row] instead -- close for the common
 * one-title-one-action case, not a faithful port of arbitrary grid placement.
 *
 * `content` carries [RowScope] so a caller can `Modifier.weight(1f)` the title/description block
 * -- without it, a title/description [Column] takes its natural (unbounded) width and a trailing
 * action is pushed past the card's own bounds instead of pinned to the trailing edge. Found by
 * rendering the actual PNG: the action button rendered outside the card entirely.
 */
context(_: Composer)
fun ShadcnCardHeader(
    modifier: Modifier = Modifier,
    content: context(Composer) RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Tw.Spacing.s6),
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        verticalAlignment = Alignment.Top,
    ) {
        content()
    }
}

/** shadcn's `CardContent`: the `px-6` inset upstream's container itself does not carry. */
context(_: Composer)
fun ShadcnCardContent(
    modifier: Modifier = Modifier,
    content: context(Composer) () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Tw.Spacing.s6)) {
        content()
    }
}

/**
 * shadcn's `CardFooter`: `flex items-center px-6`.
 *
 * Upstream also adds `pt-6` conditionally, only when a `border-t` class is present
 * (`[.border-t]:pt-6`) -- a CSS sibling-selector trick this engine has no equivalent for. Not
 * carried over; a caller that wants the border-plus-gap look adds its own top padding/border.
 */
context(_: Composer)
fun ShadcnCardFooter(
    modifier: Modifier = Modifier,
    content: context(Composer) RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Tw.Spacing.s6),
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/**
 * The card's visual contract as a [Style] rather than a modifier chain.
 *
 * A `Style` even though a card has no interaction states today, for two reasons. It is the shape the
 * whole port targets -- a recipe applies one style rather than assembling visuals inline, which is
 * what lets the `*Style` helpers fold into their components. And adding `hovered`/`focused` later is
 * then a rule inside this block instead of a restructuring of the recipe.
 *
 * Sizes name their Tailwind step: `py-6` is `Tw.Spacing.s6`, not the 24.dp it equals.
 */
private fun ShadcnThemeValues.cardStyle(contentPadding: Dp): Style = Style {
    background(palette.card)
    border(CardBorderWidth, palette.border)
    cornerRadius(radii.xl)
    textColor(palette.cardForeground)
    // Upstream CSS Card is `py-6` only -- horizontal inset (`px-6`) lives on CardHeader/CardContent/
    // CardFooter children, not the card itself, which is why this is vertical-only. The earlier
    // both-axes stopgap (applied before those slots existed) was removed: once a caller composes a
    // real slot, the slot's own px-6 stacked with this container's, roughly doubling the horizontal
    // inset -- found by rendering the actual page. A caller still using the raw `content` slot
    // directly (not the CardHeader/CardContent/CardFooter slots) supplies its own horizontal
    // padding now, the same way it already had to for vertical spacing between its own children.
    // The 1px border is part of the border box, so it is included in the effective vertical inset
    // this retained engine's inside-painted border needs.
    contentPadding(
        horizontal = CardBorderWidth,
        vertical = contentPadding + CardBorderWidth,
    )
}

/**
 * Tailwind's bare `border` is 1px.
 *
 * Not `Tw.Border.px1`: the generated scale is `Spacing`, `Radius` and `Text` only, so Tailwind's
 * border-width steps (`border-0`/`border`/`border-2`/`border-4`/`border-8`) have no named home yet.
 * A literal with the class beside it is the honest version until the generator emits them.
 */
private val CardBorderWidth: Dp = 1.dp
