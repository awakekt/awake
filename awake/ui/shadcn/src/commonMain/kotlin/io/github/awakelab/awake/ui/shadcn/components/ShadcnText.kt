/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.text.Text
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.semantics.SemanticsProperties
import io.github.awakelab.awake.compose.ui.semantics.SemanticsRole
import io.github.awakelab.awake.compose.ui.semantics.semantics
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.math2d.Sp
import io.github.awakelab.awake.core.text.font.FontWeight
import io.github.awakelab.awake.core.text.theme.TextStyle
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

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
) {
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
        if (color != null) styled.copy(color = color) else styled,
    )
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
