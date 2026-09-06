/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.tailwind.Tw

/**
 * shadcn's button sizes, carrying their own geometry.
 *
 * `Default` is **`h-9` -- 36dp**, which is worth stating because a Material-flavoured 40dp once
 * propagated up from a `ui-headless` fallback and `awake-ui-authoring` still cites that as live. It
 * is not: the existing `ShadcnButtonSize.Md` already reads 36. The skill's claim is stale and the
 * value is right.
 *
 * The icon sizes are square by construction rather than by a caller passing equal numbers, because
 * upstream spells them `size-9`/`size-6`/`size-8`/`size-10` -- one class, one dimension.
 */
enum class ShadcnButtonSizeVariant(
    internal val height: Dp,
    internal val paddingX: Dp,
    internal val paddingY: Dp = Tw.Spacing.s0,
    internal val square: Boolean = false,
    internal val text: ShadcnTextVariant = ShadcnTextVariant.Small,
) {
    /** Translates `h-9 px-4 py-2` -- the only size upstream pads vertically. */
    Default(36.dp, Tw.Spacing.s4, paddingY = Tw.Spacing.s2),

    /** Translates `h-6 px-2 text-xs`. */
    Xs(24.dp, Tw.Spacing.s2),

    /** Translates `h-8 px-3`. */
    Sm(32.dp, Tw.Spacing.s3),

    /** Translates `h-10 px-6`. */
    Lg(40.dp, Tw.Spacing.s6),

    /** Translates `size-9`. */
    Icon(36.dp, Tw.Spacing.s0, square = true),

    /** Translates `size-6`. */
    IconXs(24.dp, Tw.Spacing.s0, square = true),

    /** Translates `size-8`. */
    IconSm(32.dp, Tw.Spacing.s0, square = true),

    /** Translates `size-10`. */
    IconLg(40.dp, Tw.Spacing.s0, square = true),
}
