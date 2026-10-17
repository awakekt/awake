/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.ShadcnDensity
import com.awakekt.awake.ui.shadcn.theme.isCompact

/** `h-9` (Source Input), or `h-7` at [ShadcnDensity.Compact]: the height of an input, an input group and a select trigger. */
internal fun ShadcnDensity.fieldHeight(): Dp = if (isCompact) Tw.Spacing.s7 else Tw.Spacing.s9

/** `px-3`, or `px-2` at [ShadcnDensity.Compact]. */
internal fun ShadcnDensity.fieldPaddingX(): Dp = if (isCompact) Tw.Spacing.s2 else Tw.Spacing.s3

/** The text input's `py-1`, or `py-0.5` at [ShadcnDensity.Compact]. */
internal fun ShadcnDensity.inputPaddingY(): Dp = if (isCompact) Tw.Spacing.s0_5 else Tw.Spacing.s1

/** The textarea's `py-2`, or `py-1` at [ShadcnDensity.Compact]. */
internal fun ShadcnDensity.textareaPaddingY(): Dp = if (isCompact) Tw.Spacing.s1 else Tw.Spacing.s2

/** The select trigger's `py-2`; at [ShadcnDensity.Compact] the 28dp height alone centres its line. */
internal fun ShadcnDensity.selectPaddingY(): Dp = if (isCompact) Tw.Spacing.s0 else Tw.Spacing.s2
