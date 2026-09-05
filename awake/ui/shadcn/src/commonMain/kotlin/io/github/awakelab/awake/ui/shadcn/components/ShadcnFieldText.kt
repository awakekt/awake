/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.draw.alpha
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.Sp
import io.github.awakelab.awake.compose.ui.unit.sp
import io.github.awakelab.awake.core.text.font.FontWeight
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * The text-bearing parts of shadcn's `Field` family: legend, label, title, description, error.
 *
 * Split from the containers in `ShadcnField.kt` because every one of these is a `text-sm` with a
 * different weight, colour or leading, and every one of those three was wrong on the first pass --
 * a legend missing its `mb-3`, a description and an error at weight 500 where the browser says 400.
 * Keeping them together keeps the four line-height and weight constants in one place to compare.
 */

/**
 * `FieldLegend`: the heading of a field set.
 *
 * `variant` is upstream's, and it is a size rather than a style -- `legend` is `text-base` and
 * `label` is `text-sm`, both `font-medium`.
 */
context(_: Composer)
fun shadcnFieldLegend(
    text: String,
    modifier: Modifier = Modifier,
    variant: ShadcnFieldLegendVariant = ShadcnFieldLegendVariant.Legend,
) {
    ShadcnText(
        text,
        modifier,
        variant = when (variant) {
            ShadcnFieldLegendVariant.Legend -> ShadcnTextVariant.P
            ShadcnFieldLegendVariant.Label -> ShadcnTextVariant.Small
        },
        weight = LegendWeight,
    )
    // `mb-3`, *on top of* the field set's own `gap-6`: upstream puts 36px between a legend and the
    // group under it and 24px between everything else. Measured in the browser at 12px margin
    // against a 24px gap -- a legend that only took the gap sat 12px too close, which reads as the
    // heading belonging to the first field rather than to the set.
    Spacer(Modifier.height(LegendBottomMargin))
}

/** Which size a [shadcnFieldLegend] takes. */
enum class ShadcnFieldLegendVariant { Legend, Label }

/**
 * `FieldLabel`: the name of a control.
 *
 * Renders its own text rather than delegating to [ShadcnLabel], because upstream's `FieldLabel` is
 * `Label` *plus* `leading-snug` -- 1.375 against `text-sm`'s own 1.43, a 1px shorter line box. A
 * plain label elsewhere keeps its own leading, so the override belongs here and not there.
 */
context(_: Composer)
fun ShadcnFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ShadcnText(
        text,
        if (enabled) modifier else modifier.alpha(DISABLED_ALPHA),
        variant = ShadcnTextVariant.Small,
        lineHeight = SnugLineHeight,
    )
}

/**
 * `FieldTitle`: the label of a field that has no control to point at.
 *
 * Upstream emits `data-slot="field-label"` for both, so they are the same thing to a reader; the
 * split exists because a real `<label>` needs something to be `for`.
 */
context(_: Composer)
fun shadcnFieldTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    ShadcnText(text, modifier, variant = ShadcnTextVariant.Small, lineHeight = SnugLineHeight)
}

/** `FieldDescription`: `text-sm text-muted-foreground`, under the label or the control. */
context(_: Composer)
fun shadcnFieldDescription(
    text: String,
    modifier: Modifier = Modifier,
) {
    // `leading-normal` -- 1.5 against `text-sm`'s own 1.43. One pixel per line, and a description
    // is the part most likely to wrap to several.
    ShadcnText(text, modifier, variant = ShadcnTextVariant.Muted, lineHeight = NormalLineHeight)
}

/**
 * `FieldError`: what is wrong, in the destructive colour.
 *
 * Takes the messages rather than a rendered string, because upstream de-duplicates them and shows
 * one line for a single message and a list for several. Duplicates are the normal case with a form
 * library, where two rules on one input often fail with the same text.
 *
 * Nothing is drawn when there is nothing to say -- upstream returns `null`, and a field that
 * reserves an empty error row moves its whole form the first time one appears.
 */
context(_: Composer)
fun shadcnFieldError(
    messages: List<String>,
    modifier: Modifier = Modifier,
) {
    val unique = messages.filter { it.isNotBlank() }.distinct()
    if (unique.isEmpty()) return
    val theme = shadcnTheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
        unique.forEach { message ->
            // `text-sm font-normal`: the Muted variant is the one carrying `font-normal`, and the
            // explicit colour overrides its muted foreground. Reaching for `Small` gave weight 500
            // against the browser's 400 -- an error message noticeably bolder than upstream's.
            ShadcnText(
                message,
                variant = ShadcnTextVariant.Muted,
                color = theme.palette.destructive,
            )
        }
    }
}

/** The single-message spelling, which is the common one. */
context(_: Composer)
fun shadcnFieldError(
    message: String,
    modifier: Modifier = Modifier,
) = shadcnFieldError(listOf(message), modifier)

/** `font-medium` on the legend. */
private val LegendWeight = FontWeight.Medium

/** `mb-3` on the legend, added to the field set's gap rather than replacing it. */
private val LegendBottomMargin: Dp = Tw.Spacing.s3

/** `leading-snug` (1.375) against `text-sm`. */
private val SnugLineHeight: Sp = 19.25f.sp

/** `leading-normal` (1.5) against `text-sm`. */
private val NormalLineHeight: Sp = 21f.sp

/** `group-data-[disabled=true]/field:opacity-50`. */
private const val DISABLED_ALPHA = 0.5f
