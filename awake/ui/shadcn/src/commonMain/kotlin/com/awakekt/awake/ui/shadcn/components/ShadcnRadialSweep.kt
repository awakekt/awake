/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.ImageFill
import com.awakekt.awake.compose.ui.graphics.drawImageFill
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.sweepPath
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * A radial sweep: [value], from 0 to 1, of a full turn, filled as a pie or, with a [thickness], a
 * ring. For a cooldown over an icon, a timer, a charge or a loading dial.
 *
 * It turns from [startAngle], in degrees clockwise from 12 o'clock, clockwise unless [clockwise] is
 * false. A pie is cut to [shape], any `DrawShape`, such as a rounded square over a square icon; a
 * ring sits inside the circle the box holds.
 *
 * The fill is [color], the theme's primary by default, or [image] cut to the sweep. What is not yet
 * swept shows [trackColor], the primary at 20% as [ShadcnProgress]'s track is; pass
 * `Color.Transparent` for none, as a cooldown shade over an icon wants.
 *
 * Keywords: radial sweep, cooldown, pie, ring, radial progress, timer, dial, gauge.
 */
@Suppress("LongParameterList")
context(_: Composer)
fun ShadcnRadialSweep(
    value: Float,
    modifier: Modifier = Modifier,
    size: Dp = ShadcnRadialSweepSize,
    startAngle: Float = 0f,
    clockwise: Boolean = true,
    shape: DrawShape = DrawShape.Circle,
    thickness: Dp = 0.dp,
    color: Color? = null,
    trackColor: Color? = null,
    image: ImageFill? = null,
) {
    val theme = shadcnTheme
    val fill = color ?: theme.palette.primary
    val track = trackColor ?: theme.palette.primary.withAlpha(ShadcnProgressTrackAlpha)
    // drawBehind, not drawWithCache: the value changes every frame of a cooldown and the size does not.
    Spacer(
        modifier.size(size).drawBehind {
            drawRadialSweep(value, startAngle, clockwise, shape, thickness.value * density, fill, track, image)
        },
    )
}

/**
 * [value] of a turn over this node, as [ShadcnRadialSweep] draws it, under [shape] and over [track];
 * [ShadcnProgressBar]'s radial form draws through it too.
 */
@Suppress("LongParameterList")
internal fun DrawScope.drawRadialSweep(
    value: Float,
    startAngle: Float,
    clockwise: Boolean,
    shape: DrawShape,
    thickness: Float,
    fill: Color,
    track: Color,
    image: ImageFill?,
) {
    val bounds = Rectangle(0f, 0f, width.toFloat(), height.toFloat())
    if (bounds.width <= 0f || bounds.height <= 0f) return
    clippedPath(shape.toPath(bounds, density = density)) {
        if (track.a > 0f) sweepPath(bounds, 1f, thickness = thickness)?.let { drawPath(it, track) }
        val swept = sweepPath(bounds, value, startAngle, clockwise, thickness)
        if (swept != null) {
            if (image == null) drawPath(swept, fill) else clippedPath(swept) { drawImageFill(image) }
        }
    }
}

/** shadcn `size-8`, an icon button's size. */
internal val ShadcnRadialSweepSize: Dp = Tw.Spacing.s8
