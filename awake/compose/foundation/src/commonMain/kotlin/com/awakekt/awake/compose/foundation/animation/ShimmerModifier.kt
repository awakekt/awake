/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.animation

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.core.color.Color
import kotlin.math.abs

/**
 * `Modifier.shimmer()` on the retained compose engine.
 */
context(_: Composer)
fun Modifier.shimmer(
    highlight: Color = Color.White.withAlpha(0.6f),
    durationSeconds: Float = 1.2f,
): Modifier {
    val phase = rememberLoopingPhase(durationSeconds)
    return this then ShimmerElement(phase, highlight)
}

private const val SHIMMER_STRIPS = 12
private const val MIN_BAND_WIDTH = 160f
private const val BAND_WIDTH_FACTOR = 1.5f

private class ShimmerElement(
    private val phase: Float,
    private val highlight: Color,
) : ModifierNodeElement<ShimmerNode>() {
    override fun create(): ShimmerNode = ShimmerNode()

    override fun update(node: ShimmerNode) {
        node.phase = phase
        node.highlight = highlight
    }
}

private class ShimmerNode :
    Modifier.Node(),
    DrawModifierNode {
    var phase: Float = 0f
    lateinit var highlight: Color

    override fun DrawScope.draw(drawContent: () -> Unit) {
        drawContent()
        val bandWidth = (width * BAND_WIDTH_FACTOR).coerceAtLeast(MIN_BAND_WIDTH)
        val bandX = -bandWidth + (width + bandWidth) * phase
        val stripWidth = bandWidth / SHIMMER_STRIPS
        for (i in 0 until SHIMMER_STRIPS) {
            val t = (i + 0.5f) / SHIMMER_STRIPS
            val envelope = 1f - abs(t - 0.5f) * 2f
            drawRect(
                x = bandX + i * stripWidth,
                width = stripWidth + 1f,
                color = highlight.withAlpha(highlight.a * envelope),
            )
        }
    }

    override fun toString(): String = "shimmer()"
}
