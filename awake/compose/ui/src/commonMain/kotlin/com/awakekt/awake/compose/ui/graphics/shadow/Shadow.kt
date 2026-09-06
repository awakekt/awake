/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics.shadow

import com.awakekt.awake.compose.ui.graphics.Brush
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.DpOffset
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color

/**
 * Parameters for [com.awakekt.awake.compose.ui.draw.dropShadow].
 *
 * [brush], when supplied, takes precedence over [color]. The supported brush subset is defined by
 * [Brush]: solid colour and four-corner linear gradient. Blend modes need layer compositing and
 * deliberately remain outside this primitive.
 */
data class Shadow(
    val radius: Dp,
    val color: Color = Color.Black,
    val spread: Dp = 0.dp,
    val offset: DpOffset = DpOffset.Zero,
    val alpha: Float = 1f,
    val brush: Brush? = null,
) {
    init {
        require(alpha in 0f..1f) { "Shadow alpha must be between 0 and 1." }
    }
}
