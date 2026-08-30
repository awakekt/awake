/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.core.math2d.Sp
import io.github.awakelab.awake.core.math2d.sp
import io.github.awakelab.awake.core.text.font.FontWeight
import io.github.awakelab.awake.tailwind.Tw

/**
 * shadcn's typography scale.
 *
 * **These are shadcn's own names, and that is what comparing all three sources produced.**
 * `shadcn-compose` names its scale `DisplayLarge`/`TitleMedium`/`BodySmall`/`LabelSmall` -- which is
 * **Material 3's** type scale, not shadcn's. Real `typography.tsx` is `h1`..`h4`, `p`, `blockquote`,
 * `lead`, `large`, `small`, `muted`, and someone translating a shadcn page needs the name that is on
 * the page. Awake already had it right; upstream settles it.
 *
 * **Each variant carries its own values rather than a `when` resolving them.** Two `when`s over nine
 * entries is a cyclomatic complexity of 15 for what is a lookup table, and detekt said so. It also
 * puts the Tailwind classes beside the name they belong to, so a drift is a line-for-line diff
 * against `typography.tsx`.
 *
 * Sizes name their Tailwind step (`Tw.Text.base`), never the `Sp` it equals -- see
 * `awake-ui-authoring`'s `references/shadcn-translation.md`.
 *
 * **A Tailwind text step is a pair.** `text-sm` is `font-size: 14px; line-height: 20px`, and the
 * generated `Tw.Text` carries only the first half, so every variant states the second here. Leaving
 * it to the font's own metrics made a dropdown item 29px tall against shadcn's 32 -- the parity
 * harness found it the first time a fixture ran against this engine. `Tw` is generated, so the pair
 * belongs in the generator eventually; see docs/tasks/README.md.
 */
enum class ShadcnTextVariant(
    internal val size: Sp,
    /** The `line-height` half of the Tailwind step, which sets the line box the row is measured by. */
    internal val lineHeight: Sp,
    internal val weight: FontWeight,
    /** Takes `text-muted-foreground`. The rest inherit, which is what lets an alert tint its own. */
    internal val muted: Boolean,
) {
    /** Translates `text-4xl font-extrabold`. */
    H1(Tw.Text.`4xl`, 40f.sp, FontWeight.ExtraBold, muted = false),

    /** Translates `text-3xl font-semibold`. */
    H2(Tw.Text.`3xl`, 36f.sp, FontWeight.SemiBold, muted = false),

    /** Translates `text-2xl font-semibold`. */
    H3(Tw.Text.`2xl`, 32f.sp, FontWeight.SemiBold, muted = false),

    /** Translates `text-xl font-semibold`. */
    H4(Tw.Text.xl, 28f.sp, FontWeight.SemiBold, muted = false),

    /** Translates `text-base`. */
    P(Tw.Text.base, 24f.sp, FontWeight.Normal, muted = false),

    /** Translates `text-xl text-muted-foreground`. */
    Lead(Tw.Text.xl, 28f.sp, FontWeight.Normal, muted = true),

    /** Translates `text-lg font-semibold`. */
    Large(Tw.Text.lg, 28f.sp, FontWeight.SemiBold, muted = false),

    /** Translates `text-sm font-medium`. */
    Small(Tw.Text.sm, 20f.sp, FontWeight.Medium, muted = false),

    /** Translates `text-sm text-muted-foreground`. */
    Muted(Tw.Text.sm, 20f.sp, FontWeight.Normal, muted = true),

    /**
     * Translates `text-xs font-medium`.
     *
     * **Not from `typography.tsx`.** That file's scale stops at `small`, but `text-xs` is applied
     * directly by badge, kbd and tooltip, and each of them was reaching for [Small] instead -- a
     * 14/20 step where upstream says 12/16, which is 4px of height on every one. It lives here so
     * the pair is stated once rather than three times.
     */
    Xs(Tw.Text.xs, 16f.sp, FontWeight.Medium, muted = false),
}
