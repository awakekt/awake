/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.ShadcnDensity
import com.awakekt.awake.ui.shadcn.theme.isCompact

/** Source Checkbox's `size-4`, or `size-3.5` at [ShadcnDensity.Compact]. */
internal fun ShadcnDensity.checkboxBoxSize(): Dp = if (isCompact) Tw.Spacing.s3_5 else Tw.Spacing.s4

/** Source Checkbox's `rounded-[4px]`; it deliberately does not follow the theme radius scale. */
internal val ShadcnCheckboxBoxRadius: Dp = 4.dp

/** Tailwind's bare `border` is 1px; its generated spacing scale has no border-width entry. */
internal val ShadcnCheckboxBorderWidth: Dp = 1.dp

/** Source `size-3.5` check glyph centered in the `size-4` control: (16dp - 14dp) / 2. */
internal val ShadcnCheckboxCheckInset: Dp = 1.dp

/** Source Checkbox's `disabled:opacity-50`. */
internal const val ShadcnCheckboxDisabledAlpha = 0.5f
