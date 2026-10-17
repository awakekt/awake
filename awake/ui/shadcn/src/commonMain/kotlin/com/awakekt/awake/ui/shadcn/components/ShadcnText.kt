/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.runtime.rememberGroup
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Sp
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * A line of shadcn-styled text.
 *
 * Colour resolution has one rule worth stating, because the recipe this replaces carried a comment
 * explaining the bug that came from getting it wrong: [color] is **nullable, and null means
 * inherit**, not "use the default". Upstream's `AlertDescription` is `text-sm` with no colour class,
 * so inside a destructive alert it stays red -- which only works if a variant leaves colour unset.
 * A non-null default makes "inherit" indistinguishable from "unspecified", and the variant's own
 * muted colour always wins.
 */
context(_: Composer)
fun ShadcnText(
    text: String,
    modifier: Modifier = Modifier,
    variant: ShadcnTextVariant = ShadcnTextVariant.P,
    color: Color? = null,
    /**
     * Overrides the variant's line box, for Tailwind's `leading-*`.
     *
     * A separate utility upstream, applied per usage rather than baked into a text step: a dialog
     * title is `text-lg leading-none`, so its line box is 18 where the `text-lg` pair says 28. That
     * 10px was the whole of the dialog's height mismatch.
     */
    lineHeight: Sp? = null,
    /**
     * Overrides the variant's weight, for Tailwind's `font-*`.
     *
     * Same reason as [lineHeight]: a weight is a separate utility upstream applies per usage. An
     * empty state's title is `text-lg font-medium` where the `Large` variant is semibold.
     */
    weight: FontWeight? = null,
) = shadcnDensityText(text, modifier, variant, color, lineHeight, weight, size = null)

/**
 * [ShadcnText] with the variant's font size replaced, which a density needs and upstream spells as a
 * separate `text-*` utility. Internal: a caller picks a [ShadcnTextVariant], and the density picks
 * the step a label or description sits at.
 */
context(_: Composer)
internal fun shadcnDensityText(
    text: String,
    modifier: Modifier = Modifier,
    variant: ShadcnTextVariant = ShadcnTextVariant.P,
    color: Color? = null,
    lineHeight: Sp? = null,
    weight: FontWeight? = null,
    size: Sp? = null,
) {
    rememberGroup(ShadcnTextGroup) {
        val theme = shadcnTheme
        // Two remembers rather than one three-key call: `remember` takes at most two keys, and a
        // composite key object would allocate every frame.
        val base = remember(theme, variant) {
            TextStyle(
                size = variant.size,
                lineHeight = variant.lineHeight,
                weight = weight ?: variant.weight,
                color = if (variant.muted) theme.palette.mutedForeground else null,
            )
        }
        val style = remember(base, lineHeight) {
            if (lineHeight == null) base else base.copy(lineHeight = lineHeight)
        }
        val styled = remember(style, weight) { if (weight == null) style else style.copy(weight = weight) }
        // Not remembered: only a non-default density sets it, and one copy per frame is the cost of
        // keeping the default path's slots exactly as they were.
        val sized = if (size == null) styled else styled.copy(size = size)
        // An explicit colour wins over the variant's; both absent leaves the ambient one.
        Text(
            text,
            // Text a reader can read. Without this a screen reader hears nothing at all from a label,
            // and only controls that declare their own semantics are addressable -- which is also why
            // an empty state's title was invisible to the parity harness.
            modifier.semantics {
                this[SemanticsProperties.Role] = SemanticsRole.Text
                this[SemanticsProperties.Label] = text
            },
            if (color != null) sized.copy(color = color) else sized,
        )
    }
}

/** `ShadcnText(variant = Muted)`, spelled out for the common case of a lone caption line. */
context(_: Composer)
fun shadcnMuted(text: String, modifier: Modifier = Modifier) =
    ShadcnText(text, modifier, variant = ShadcnTextVariant.Muted)

context(_: Composer)
fun shadcnH1(text: String, modifier: Modifier = Modifier) =
    ShadcnText(text, modifier, variant = ShadcnTextVariant.H1)

context(_: Composer)
fun shadcnH2(text: String, modifier: Modifier = Modifier) =
    ShadcnText(text, modifier, variant = ShadcnTextVariant.H2)

context(_: Composer)
fun shadcnH3(text: String, modifier: Modifier = Modifier) =
    ShadcnText(text, modifier, variant = ShadcnTextVariant.H3)

context(_: Composer)
fun shadcnH4(text: String, modifier: Modifier = Modifier) =
    ShadcnText(text, modifier, variant = ShadcnTextVariant.H4)

context(_: Composer)
fun shadcnLead(text: String, modifier: Modifier = Modifier) =
    ShadcnText(text, modifier, variant = ShadcnTextVariant.Lead)

context(_: Composer)
fun shadcnLarge(text: String, modifier: Modifier = Modifier) =
    ShadcnText(text, modifier, variant = ShadcnTextVariant.Large)

context(_: Composer)
fun shadcnSmall(text: String, modifier: Modifier = Modifier) =
    ShadcnText(text, modifier, variant = ShadcnTextVariant.Small)

/** Keys this component's `remember` slots as one group in its caller's node. */
private object ShadcnTextGroup
