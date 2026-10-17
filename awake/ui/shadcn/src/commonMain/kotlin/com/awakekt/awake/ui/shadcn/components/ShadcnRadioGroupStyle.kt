/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.math2d.Sp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.ShadcnDensity
import com.awakekt.awake.ui.shadcn.theme.isCompact

/** Source RadioGroupItem's `size-4`, or `size-3.5` at [ShadcnDensity.Compact]. */
internal fun ShadcnDensity.radioSize(): Dp = if (isCompact) Tw.Spacing.s3_5 else Tw.Spacing.s4

/** Source RadioGroup indicator's `size-2`, or `size-1.5` at [ShadcnDensity.Compact]. */
internal fun ShadcnDensity.radioDotSize(): Dp = if (isCompact) Tw.Spacing.s1_5 else Tw.Spacing.s2

/** Source Label's `gap-2` between the control and text, or `gap-1.5` at [ShadcnDensity.Compact]. */
internal fun ShadcnDensity.radioLabelGap(): Dp = if (isCompact) Tw.Spacing.s1_5 else Tw.Spacing.s2

/** Source RadioGroup's `gap-3` between repeated option rows, or `gap-2` at [ShadcnDensity.Compact]. */
internal fun ShadcnDensity.radioGroupGap(): Dp = if (isCompact) Tw.Spacing.s2 else Tw.Spacing.s3

/** Tailwind's bare `border` is 1px. */
internal val ShadcnRadioBorderWidth: Dp = 1.dp

/** Source Label's `text-sm leading-none`. */
internal val ShadcnRadioLabelLeading = Sp(14f)

/** The label's line: `leading-none` of `text-sm`, or of `text-xs` at [ShadcnDensity.Compact]. */
internal fun ShadcnDensity.radioLabelLeading(): Sp = if (isCompact) Sp(12f) else ShadcnRadioLabelLeading

/** Stable metrics for layout consumers that cannot depend on Compose unit types. */
object ShadcnRadioMetrics {
    val itemSize = com.awakekt.awake.core.math2d.Dp(16f)
    val indicatorSize = com.awakekt.awake.core.math2d.Dp(8f)
    val labelGap = com.awakekt.awake.core.math2d.Dp(8f)
    val groupGap = com.awakekt.awake.core.math2d.Dp(12f)
}
