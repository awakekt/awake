/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.FillDirection
import com.awakekt.awake.compose.ui.graphics.ImageFill
import com.awakekt.awake.compose.ui.graphics.drawImageFill
import com.awakekt.awake.compose.ui.graphics.drawImageFillBar
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.platform.LocalFrameClock
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.math.min
import kotlin.math.roundToInt

/** A Bar, a Sweep or a Stepper, each drawn by its own view. */
context(_: Composer)
internal fun GaugeView(element: CanvasElement, styled: Modifier, textStyle: TextStyle, tree: CanvasTree, children: List<CanvasItem>) {
    val bar = element.bar
    when {
        element.kind == CanvasElementKind.Sweep -> SweepView(element, styled, tree, children)
        element.kind == CanvasElementKind.Stepper -> StepperView(element, styled, textStyle, tree, children)
        bar != null -> ProgressBarView(element, styled, textStyle, tree, children)
        else -> Box(styled) {
            BarFill(element, tree.valueOf(element), colorOf(element.color, Color.White), tree.images)
            Children(element, children, tree)
        }
    }
}

/**
 * A Bar with [CanvasElement.bar] settings: its track, trail and fill drawn as they say, then its
 * label and children. A Bar without them is drawn as it always was, by [BarFill].
 */
context(_: Composer)
internal fun ProgressBarView(element: CanvasElement, styled: Modifier, textStyle: TextStyle, tree: CanvasTree, children: List<CanvasItem>) {
    val settings = element.bar ?: CanvasBar.Default
    val fill = colorOf(element.color, Color.White)
    val value = tree.valueOf(element)
    val trail = trailOf(element, settings, value, LocalFrameClock.current.deltaSeconds)
    val picture = element.style.fillImage?.fill(tree.images)
    Box(styled, contentAlignment = Alignment.Center) {
        if (settings.direction == CanvasBarDirection.Radial) {
            val sweep = CanvasSweep(thickness = settings.thickness, track = settings.track)
            if (trail > value) SweepFill(trail, colorOf(settings.trailColor, Color.Transparent), null, sweep, DrawShape.Circle, colorOf(settings.track, Color.Transparent))
            val track = if (trail > value) Color.Transparent else colorOf(settings.track, Color.Transparent)
            SweepFill(value, fill, picture, sweep, DrawShape.Circle, track)
        } else {
            val radius = element.style.cornerRadius ?: 0f
            Box(Modifier.fillMaxSize().drawBehind { drawStraightBar(element, settings, value, trail, fill, picture, radius * density) })
        }
        // The element's colour is the fill, so the label is white over it unless the style says otherwise.
        val labelStyle = if (element.style.textColor == null) textStyle.copy(color = Color.White) else textStyle
        if (settings.label) Text(tree.textOf(element).ifEmpty { "${(value * PERCENT).roundToInt()}%" }, style = labelStyle)
        Children(element, children, tree)
    }
}

/** Where [element]'s trail is this frame: on the value the first time, then trailing it as [settings] say; the value without a trail colour. */
private fun trailOf(element: CanvasElement, settings: CanvasBar, value: Float, deltaSeconds: Float): Float {
    if (settings.trailColor == null) return value
    if (!element.trailStarted) {
        element.trail.snapTo(value)
        element.trailStarted = true
    }
    return element.trail.advance(value, deltaSeconds, settings.trailDelay, settings.trailSpeed)
}

/** A straight Bar's track, trail, fill and ticks, from the edge its direction names. */
@Suppress("LongParameterList")
private fun DrawScope.drawStraightBar(element: CanvasElement, settings: CanvasBar, value: Float, trail: Float, fill: Color, picture: ImageFill?, radius: Float) {
    val direction = settings.direction.fillDirection
    val length = if (direction.vertical) height.toFloat() else width.toFloat()
    if (length <= 0f) return
    val track = colorOf(settings.track, Color.Transparent)
    val trailColor = colorOf(settings.trailColor, Color.Transparent)
    if (settings.segments > 1) {
        drawSegments(direction, settings, length, value, trail, fill, track, trailColor)
    } else {
        if (track.a > 0f) span(direction, 0f, length, track, radius)
        if (trail > value) span(direction, 0f, trail * length, trailColor, radius)
        when {
            picture == null -> if (value > 0f) span(direction, 0f, value * length, fill, radius)
            element.style.fillImageMode == CanvasFillImageMode.Capped -> drawImageFillBar(picture, value, direction)
            value > 0f -> drawPictureFill(direction, picture, value * length, element.style.fillImageMode == CanvasFillImageMode.Squeeze)
        }
    }
    val tick = colorOf(settings.tickColor, Color.Transparent)
    for (i in 1 until settings.ticks) {
        val at = (length * i / settings.ticks).toInt().toFloat()
        span(direction, at, at + density, tick, 0f)
    }
}

/**
 * [picture] filling [filled] of the bar from the edge [direction] starts at: drawn at that length
 * when [squeezed], so both its ends show, or laid out over the whole bar and cut there.
 */
private fun DrawScope.drawPictureFill(direction: FillDirection, picture: ImageFill, filled: Float, squeezed: Boolean) {
    val w = width.toFloat()
    val h = height.toFloat()
    val x = if (direction == FillDirection.RightToLeft) w - filled else 0f
    val y = if (direction == FillDirection.BottomToTop) h - filled else 0f
    val boxWidth = if (direction.vertical) w else filled
    val boxHeight = if (direction.vertical) filled else h
    if (squeezed) {
        drawImageFill(picture, x, y, boxWidth, boxHeight)
    } else {
        clippedPath(DrawShape.Rectangle.toPath(Rectangle(x, y, boxWidth, boxHeight))) { drawImageFill(picture) }
    }
}

/** [CanvasBar.segments] blocks with gaps, each a track filled as far as [value] of their combined length reaches. */
@Suppress("LongParameterList")
private fun DrawScope.drawSegments(direction: FillDirection, settings: CanvasBar, length: Float, value: Float, trail: Float, fill: Color, track: Color, trailColor: Color) {
    val segments = settings.segments
    val gap = settings.segmentGap * density
    val block = (length - gap * (segments - 1)) / segments
    if (block <= 0f) return
    val across = if (direction.vertical) width.toFloat() else height.toFloat()
    val radius = min(across, block) / SEGMENT_ROUNDING
    for (i in 0 until segments) {
        val start = i * (block + gap)
        if (track.a > 0f) span(direction, start, start + block, track, radius)
        val trailed = (trail * block * segments - i * block).coerceIn(0f, block)
        if (trail > value && trailed > 0f) span(direction, start, start + trailed, trailColor, radius)
        val filled = (value * block * segments - i * block).coerceIn(0f, block)
        if (filled > 0f) span(direction, start, start + filled, fill, radius)
    }
}

/** [from] to [to] along the bar, measured from the edge [direction] starts at, its whole width across. */
private fun DrawScope.span(direction: FillDirection, from: Float, to: Float, color: Color, radius: Float) {
    if (to <= from || color.a <= 0f) return
    val w = width.toFloat()
    val h = height.toFloat()
    when (direction) {
        FillDirection.LeftToRight -> drawRoundedRect(x = from, y = 0f, width = to - from, height = h, color = color, radius = radius)
        FillDirection.RightToLeft -> drawRoundedRect(x = w - to, y = 0f, width = to - from, height = h, color = color, radius = radius)
        FillDirection.TopToBottom -> drawRoundedRect(x = 0f, y = from, width = w, height = to - from, color = color, radius = radius)
        FillDirection.BottomToTop -> drawRoundedRect(x = 0f, y = h - to, width = w, height = to - from, color = color, radius = radius)
    }
}

private val CanvasBarDirection.fillDirection: FillDirection
    get() = when (this) {
        CanvasBarDirection.RightToLeft -> FillDirection.RightToLeft
        CanvasBarDirection.BottomToTop -> FillDirection.BottomToTop
        CanvasBarDirection.TopToBottom -> FillDirection.TopToBottom
        CanvasBarDirection.LeftToRight, CanvasBarDirection.Radial -> FillDirection.LeftToRight
    }

/** A segment's corners are rounded by a quarter of its smaller side. */
private const val SEGMENT_ROUNDING = 4f

private const val PERCENT = 100f
