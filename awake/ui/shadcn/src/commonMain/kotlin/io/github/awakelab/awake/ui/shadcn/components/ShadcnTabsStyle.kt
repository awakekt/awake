/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.ui.graphics.shadow.Shadow
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.DpOffset
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.ui.shadcn.ShadcnResolvedTheme

/** Source TabsList's horizontal `h-9`. */
internal val ShadcnTabsHeight: Dp = 36.dp

/** Source TabsList's `p-[3px]`; it is intentionally not a Tailwind scale value. */
internal val ShadcnTabsListPadding: Dp = 3.dp

/** Source inactive TabsTrigger's `text-foreground/60`. */
internal const val ShadcnTabsInactiveAlpha = 0.6f

/** Tailwind v4's `shadow-sm`: `0 1px 2px 0 rgb(0 0 0 / 0.05)`. */
internal fun shadcnTabsActiveShadow(theme: ShadcnResolvedTheme): Shadow = Shadow(
    radius = 2.dp,
    color = theme.palette.shadow,
    offset = DpOffset(0.dp, 1.dp),
    alpha = 0.05f,
)
