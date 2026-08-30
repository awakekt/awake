/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.math2d.Sp
import io.github.awakelab.awake.tailwind.Tw

/** Source RadioGroupItem's `size-4`. */
internal val ShadcnRadioSize: Dp = Tw.Spacing.s4

/** Source RadioGroup indicator's `size-2`. */
internal val ShadcnRadioDotSize: Dp = Tw.Spacing.s2

/** Source Label's `gap-2` between the control and text. */
internal val ShadcnRadioLabelGap: Dp = Tw.Spacing.s2

/** Source RadioGroup's `gap-3` between repeated option rows. */
internal val ShadcnRadioGroupGap: Dp = Tw.Spacing.s3

/** Tailwind's bare `border` is 1px. */
internal val ShadcnRadioBorderWidth: Dp = 1.dp

/** Source Label's `text-sm leading-none`. */
internal val ShadcnRadioLabelLeading = Sp(14f)

/** Stable metrics for layout consumers that cannot depend on Compose unit types. */
object ShadcnRadioMetrics {
    val itemSize = io.github.awakelab.awake.core.math2d.Dp(16f)
    val indicatorSize = io.github.awakelab.awake.core.math2d.Dp(8f)
    val labelGap = io.github.awakelab.awake.core.math2d.Dp(8f)
    val groupGap = io.github.awakelab.awake.core.math2d.Dp(12f)
}
