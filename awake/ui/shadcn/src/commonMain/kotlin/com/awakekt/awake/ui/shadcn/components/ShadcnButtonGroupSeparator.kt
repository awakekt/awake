/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * The hairline between two buttons in a group.
 *
 * Takes its orientation as an argument rather than reading it from a group context. The ui-core
 * version read `LocalShadcnButtonGroup`, which meant a separator outside a group silently drew the
 * wrong way; here it cannot be wrong without being written wrong.
 */
context(_: Composer)
fun ShadcnButtonGroupSeparator(
    modifier: Modifier = Modifier,
    orientation: ShadcnButtonGroupOrientation = ShadcnButtonGroupOrientation.Horizontal,
) {
    val theme = shadcnTheme
    // A horizontal group is separated by a vertical hairline, and vice versa.
    val sized = when (orientation) {
        ShadcnButtonGroupOrientation.Horizontal -> modifier.width(SeparatorThickness).fillMaxHeight()
        ShadcnButtonGroupOrientation.Vertical -> modifier.height(SeparatorThickness).fillMaxWidth()
    }
    Box(sized.background(theme.palette.border))
}

/** Tailwind's bare `border`. */
private val SeparatorThickness: Dp = 1.dp
