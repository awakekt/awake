/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.Canvas
import io.github.awakelab.awake.compose.foundation.animation.rememberLoopingPhase
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.graphics2d.DrawStroke
import io.github.awakelab.awake.core.graphics2d.StrokeCap
import io.github.awakelab.awake.core.graphics2d.StrokeJoin
import io.github.awakelab.awake.core.graphics2d.drawPath
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme
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
        val drawn = (SEGMENTS * 3) / 4
        val arc = drawPath {
            val firstAngle = turn
            moveTo(cx + cos(firstAngle) * radius, cy + sin(firstAngle) * radius)
            for (i in 1 until drawn) {
                val angle = turn + i * 2f * PI.toFloat() / SEGMENTS
                lineTo(cx + cos(angle) * radius, cy + sin(angle) * radius)
            }
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

/**
 * Enough that consecutive stamps overlap into one stroke.
 *
 * At `size-6` the ring's circumference is about 69px and the stroke is 2px, so 24 segments left
 * visible gaps and the spinner read as a ring of dots. This is that count rounded up past the
 * point where they touch.
 */
private const val SEGMENTS = 48
