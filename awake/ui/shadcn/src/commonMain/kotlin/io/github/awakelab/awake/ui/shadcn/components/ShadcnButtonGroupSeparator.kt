/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.fillMaxHeight
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

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
