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
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.StrokeCap
import com.awakekt.awake.core.graphics2d.StrokeJoin
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.drawPath
import com.awakekt.awake.core.graphics2d.tessellateStrokeAa
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's spinner: `size-6 animate-spin rounded-full border-2 border-current border-t-transparent`.
 *
 * A ring with one quarter missing, rotated. The gap is what makes rotation visible at all -- a full
 * ring spinning is indistinguishable from a still one, which is the bug a spinner drawn as a plain
 * circle always has.
 *
 * The arc is tessellated once and emitted as a rotating mesh. Rebuilding a stroked path in the
 * Canvas lambda made every frame pay curve flattening, stroke expansion and fill tessellation,
 * which was the source of the reported rough motion. The stable mesh follows the official SVG
 * implementation more closely: fixed geometry plus a transform-driven spin.
 */
context(_: Composer)
fun ShadcnSpinner(
    modifier: Modifier = Modifier,
    size: Dp = SpinnerSize,
) {
    val theme = shadcnTheme
    val phase = rememberLoopingPhase(SPIN_SECONDS)
    val density = LocalDensity.current
    val mesh = remember(size, density) {
        SpinnerMeshCache(size, density)
    }.mesh(theme.palette.primary)
    Canvas(modifier.size(size)) {
        // `emit` expects tree-space coordinates and bypasses this Canvas node's origin. Rotate the
        // cached local mesh first, then hand it to drawMesh so padding/offsets from the showcase
        // layout are applied. The old direct emit left nested spinners at the root origin, where a
        // parent clip could make them appear to vanish.
        drawMesh(
            UiDrawPrimitive.Mesh(
                mesh = mesh,
                rotationDegrees = phase * 360f,
                pivotX = width / 2f,
                pivotY = height / 2f,
            ).placedMesh(),
        )
    }
}

/** Holds the geometry cache separately from the theme colour so theme changes do not rebuild it. */
private class SpinnerMeshCache(
    private val size: Dp,
    private val density: Float,
) {
    private var color: Color? = null
    private var cached: ColoredTriangleMesh? = null

    fun mesh(nextColor: Color): ColoredTriangleMesh {
        if (nextColor == color) return requireNotNull(cached)
        val sizeDp = size.value
        val radius = (sizeDp - SpinnerStroke.value) / 2f
        val centre = sizeDp / 2f
        val path = drawPath {
            moveTo(centre + radius, centre)
            arcTo(
                left = centre - radius,
                top = centre - radius,
                right = centre + radius,
                bottom = centre + radius,
                startDegrees = 0f,
                sweepDegrees = 270f,
            )
        }
        val built = path.tessellateStrokeAa(
            stroke = DrawStroke(
                width = SpinnerStroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
            color = nextColor,
            density = density,
        )
        color = nextColor
        cached = built
        return built
    }
}

/** `size-6`. */
private val SpinnerSize: Dp = 24.dp

/** `border-2`. */
private val SpinnerStroke: Dp = 2.dp

/** `animate-spin` is one turn per second. */
private const val SPIN_SECONDS = 1f
