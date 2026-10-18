/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.ImageFill
import com.awakekt.awake.compose.ui.graphics.drawImageFill
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.sweepPath
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.math2d.Rectangle

/**
 * [value]'s share of a full turn over the element's whole box, as [sweep] turns it: [color], or
 * [picture] cut to the same sweep, over [track] where it has not swept, all cut to [mask]. An action
 * button's cooldown and a Sweep element draw through it, and a Bar's
 * [CanvasFillImageMode.Sweep]; with the defaults it is a pie clockwise from 12 o'clock, cut to the box.
 */
@Suppress("LongParameterList")
context(_: Composer)
internal fun SweepFill(
    value: Float,
    color: Color,
    picture: ImageFill?,
    sweep: CanvasSweep = CanvasSweep.Default,
    mask: DrawShape = DrawShape.Rectangle,
    track: Color = Color.Transparent,
) {
    val amount = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
    if (amount <= 0f && track.a <= 0f) return
    Box(
        Modifier.fillMaxSize().drawBehind {
            val ring = sweep.thickness * density
            // A full pie needs no wedge: its start and end would be the same point, and asking the
            // arc tessellator to close exactly back onto it leaves a hairline, float-precision gap.
            val bare = track.a <= 0f && mask == DrawShape.Rectangle
            if (amount >= 1f && ring <= 0f && bare) {
                if (picture == null) drawRect(color = color) else drawImageFill(picture)
                return@drawBehind
            }
            val bounds = Rectangle(0f, 0f, width.toFloat(), height.toFloat())
            masked(mask, bounds) {
                if (track.a > 0f) sweepPath(bounds, 1f, thickness = ring)?.let { drawPath(it, track) }
                val swept = sweepPath(bounds, amount, sweep.startAngle, sweep.clockwise, ring)
                if (swept != null) {
                    if (picture == null) drawPath(swept, color) else clippedPath(swept) { drawImageFill(picture) }
                }
            }
        },
    )
}

/** [block] clipped to [mask] over [bounds]: the box itself as a plain clip, any other shape as its path. */
private fun DrawScope.masked(mask: DrawShape, bounds: Rectangle, block: () -> Unit) {
    if (mask == DrawShape.Rectangle) clipped(block = block) else clippedPath(mask.toPath(bounds, density = density), block = block)
}

/** The outline this element's [CanvasElement.sweep] is cut to, its corners from its style's `cornerRadius`. */
internal fun CanvasElement.sweepMask(): DrawShape {
    val radius = style.cornerRadius ?: 0f
    return when ((sweep ?: CanvasSweep.Default).mask) {
        CanvasMask.Box -> if (radius > 0f) DrawShape.RoundedRectangle(radius.dp) else DrawShape.Rectangle
        CanvasMask.Circle -> DrawShape.Circle
        CanvasMask.Pill -> DrawShape.Pill
        CanvasMask.CutCorner -> if (radius > 0f) DrawShape.CutCorner(radius.dp) else DrawShape.Rectangle
    }
}

/** A Sweep element: its value swept over its box as its [CanvasElement.sweep] says, in its colour or fill image. */
context(_: Composer)
internal fun SweepView(element: CanvasElement, styled: Modifier, tree: CanvasTree, children: List<CanvasItem>) {
    val settings = element.sweep ?: CanvasSweep.Default
    val fill = colorOf(element.color, Color.White)
    Box(styled) {
        SweepFill(
            tree.valueOf(element),
            fill,
            element.style.fillImage?.fill(tree.images),
            settings,
            element.sweepMask(),
            colorOf(settings.track, Color.Transparent),
        )
        Children(element, children, tree)
    }
}
