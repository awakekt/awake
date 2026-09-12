/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.Canvas
import com.awakekt.awake.compose.foundation.animation.rememberLoopingPhase
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.StrokeCap
import com.awakekt.awake.core.graphics2d.StrokeJoin
import com.awakekt.awake.core.graphics2d.drawPath
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * shadcn's spinner: `size-6 animate-spin rounded-full border-2 border-current border-t-transparent`.
 *
 * A ring with one quarter missing, rotated. The gap is what makes rotation visible at all -- a full
 * ring spinning is indistinguishable from a still one, which is the bug a spinner drawn as a plain
 * circle always has.
 *
 * Drawn as one segmented arc path because `DrawScope` has no arc primitive. A continuous stroke
 * avoids the lumpy dot-ring result produced by independent rounded quads at small sizes.
 */
context(_: Composer)
fun ShadcnSpinner(
    modifier: Modifier = Modifier,
    size: Dp = SpinnerSize,
) {
    val theme = shadcnTheme
    val phase = rememberLoopingPhase(SPIN_SECONDS)
    Canvas(modifier.size(size)) {
        val stroke = SpinnerStroke.value * density
        val radius = (width.coerceAtMost(height) - stroke) / 2f
        val cx = width / 2f
        val cy = height / 2f
        val turn = phase * 2f * PI.toFloat()
        // Three quarters of the ring: `border-t-transparent` leaves the top edge unpainted.
        val arc = drawPath {
            val startDegrees = turn * 180f / PI.toFloat()
            moveTo(cx + cos(turn) * radius, cy + sin(turn) * radius)
            arcTo(
                left = cx - radius,
                top = cy - radius,
                right = cx + radius,
                bottom = cy + radius,
                startDegrees = startDegrees,
                sweepDegrees = 270f,
            )
        }
        drawStrokedPath(
            arc,
            DrawStroke(
                width = stroke.dp,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
            theme.palette.mutedForeground,
        )
    }
}

/** `size-6`. */
private val SpinnerSize: Dp = 24.dp

/** `border-2`. */
private val SpinnerStroke: Dp = 2.dp

/** `animate-spin` is one turn per second. */
private const val SPIN_SECONDS = 1f
