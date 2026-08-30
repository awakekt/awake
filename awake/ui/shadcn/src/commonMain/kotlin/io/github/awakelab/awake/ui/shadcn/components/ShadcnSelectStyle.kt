/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.style.Style
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.ShadcnThemeValues

/** `h-9`; this is a Tailwind scale value, not an unlabelled 36dp metric. */
internal val ShadcnSelectHeight: Dp = Tw.Spacing.s9

/** The item-aligned reference popup is 160px tall. */
internal val ShadcnSelectOpenHeight: Dp = 160.dp

/** The Select surface's inner height after its `p-1` plus 1px border inset. */
internal val ShadcnSelectSurfaceInset: Dp = Tw.Spacing.s1 + POPOVER_BORDER_WIDTH

internal val ShadcnSelectContentHeight: Dp =
    ShadcnSelectOpenHeight - (ShadcnSelectSurfaceInset.value * 2f).dp

/** The viewport height when neither overflow scroll affordance is needed. */
internal val ShadcnSelectBaseViewportHeight: Dp = ShadcnSelectContentHeight

/** Radix's item-aligned scroll-up affordance, measured from the pinned reference capture. */
internal val ShadcnSelectScrollButtonHeight: Dp = 24.dp

/** The visible Select viewport after the 23dp item-aligned scroll control. */
internal val ShadcnSelectViewportHeight: Dp = 127.dp

/** `py-1.5` plus the 14px small text line-height resolves to a 32dp Select row. */
internal val ShadcnSelectItemHeight: Dp = Tw.Spacing.s8

/** A compact glyph centered in the scroll button. */
internal val ShadcnSelectScrollIconSize: Dp = Tw.Spacing.s4

/** `size-4` on `SelectItem`'s check icon. */
internal val ShadcnSelectIndicatorSize: Dp = Tw.Spacing.s4
internal val ShadcnSelectIndicatorSlotSize: Dp = Tw.Spacing.s6

/** Default SelectContent has no `sideOffset`; DropdownMenuContent does. */
internal val SelectPopupGap: Dp = 0.dp

internal fun ShadcnThemeValues.selectTriggerStyle(): Style = fieldStyle(verticalPadding = Tw.Spacing.s2)
