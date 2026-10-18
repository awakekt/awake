/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.FillDirection
import com.awakekt.awake.compose.ui.graphics.ImageFill
import com.awakekt.awake.compose.ui.graphics.drawImageFill
import com.awakekt.awake.compose.ui.graphics.drawImageFillBar
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme
import kotlin.math.min

/**
 * A game-style progress bar: [ShadcnProgress]'s track and fill, plus what a health, mana or
 * experience bar needs.
 *
 * - [orientation]: across, up, or around as a ring [thickness] wide.
 * - [trailing]: a second fill behind the first, in [trailingColor], for damage just taken. Pass the
 *   value from `animateTrailing(value)`, which holds and then drains it, or any value of your own.
 *   It only shows above [value].
 * - [segments]: the bar as that many blocks with gaps between them, for pips such as a stamina
 *   bar's. [ticks]: that many equal parts marked by thin lines across the bar, as on a gauge.
 *   Both apply to a straight bar.
 * - [label]: words centred on the bar, such as `"75 / 100"`.
 * - [fill] and [track]: nine-slice art in place of the colours. The fill keeps both end caps whole
 *   at every value, as `drawImageFillBar` draws it.
 *
 * Colours default to the theme's primary, on the primary at 20% for the track, and the
 * destructive colour for the trail.
 *
 * Keywords: progress bar, health bar, resource bar, meter, gauge, experience bar, segmented bar.
 */
@Suppress("LongParameterList")
context(_: Composer)
fun ShadcnProgressBar(
    value: Float,
    modifier: Modifier = Modifier,
    orientation: ShadcnProgressOrientation = ShadcnProgressOrientation.Horizontal,
    thickness: Dp = ShadcnProgressHeight,
    trailing: Float? = null,
    segments: Int = 0,
    ticks: Int = 0,
    label: String? = null,
    fill: ImageFill? = null,
    track: ImageFill? = null,
    color: Color? = null,
    trackColor: Color? = null,
    trailingColor: Color? = null,
) {
    val theme = shadcnTheme
    val colors = BarColors(
        fill = color ?: theme.palette.primary,
        track = trackColor ?: theme.palette.primary.withAlpha(ShadcnProgressTrackAlpha),
        trail = trailingColor ?: theme.palette.destructive,
        tick = theme.palette.background,
    )
    val amount = value.coerceIn(0f, 1f)
    val trail = trailing?.coerceIn(0f, 1f)?.takeIf { it > amount }
    val sized = when (orientation) {
        ShadcnProgressOrientation.Horizontal -> modifier.fillMaxWidth().height(thickness)
        ShadcnProgressOrientation.Vertical -> modifier.fillMaxHeight().width(thickness)
        ShadcnProgressOrientation.Radial -> modifier.size(ShadcnRadialProgressSize)
    }
    // drawBehind, not drawWithCache, for the same reason as ShadcnProgress: the value moves and the size does not.
    val drawn = sized.drawBehind {
        if (orientation == ShadcnProgressOrientation.Radial) {
            val ring = thickness.value * density
            if (trail != null) drawRadialSweep(trail, 0f, true, DrawShape.Circle, ring, colors.trail, colors.track, null)
            drawRadialSweep(amount, 0f, true, DrawShape.Circle, ring, colors.fill, if (trail == null) colors.track else Color.Transparent, null)
        } else {
            drawStraightBar(orientation == ShadcnProgressOrientation.Vertical, amount, trail, segments, ticks, fill, track, colors)
        }
    }
    Box(drawn, contentAlignment = Alignment.Center) {
        if (label != null) ShadcnText(label, variant = ShadcnTextVariant.Small)
    }
}

/** The colours a [ShadcnProgressBar] draws in. */
private class BarColors(val fill: Color, val track: Color, val trail: Color, val tick: Color)

/** A straight bar's track, trail, fill and ticks, up from the bottom when [vertical], else across from the left. */
@Suppress("LongParameterList")
private fun DrawScope.drawStraightBar(
    vertical: Boolean,
    value: Float,
    trail: Float?,
    segments: Int,
    ticks: Int,
    fill: ImageFill?,
    track: ImageFill?,
    colors: BarColors,
) {
    val length = if (vertical) height.toFloat() else width.toFloat()
    val across = if (vertical) width.toFloat() else height.toFloat()
    if (length <= 0f || across <= 0f) return
    val direction = if (vertical) FillDirection.BottomToTop else FillDirection.LeftToRight
    if (segments > 1) {
        drawSegments(vertical, segments, length, across, value, trail, colors)
    } else {
        val radius = across / 2f
        if (track != null) drawImageFill(track) else span(vertical, 0f, length, colors.track, radius)
        if (trail != null) span(vertical, 0f, trail * length, colors.trail, radius)
        when {
            fill != null -> drawImageFillBar(fill, value, direction)
            value > 0f -> span(vertical, 0f, value * length, colors.fill, radius)
        }
    }
    for (i in 1 until ticks) {
        val at = (length * i / ticks).toInt().toFloat()
        span(vertical, at, at + density, colors.tick, 0f)
    }
}

/**
 * [segments] blocks with a gap between each: every block a track, filled as far as [value] of the
 * blocks' combined length reaches, and trailed to [trail].
 */
@Suppress("LongParameterList")
private fun DrawScope.drawSegments(vertical: Boolean, segments: Int, length: Float, across: Float, value: Float, trail: Float?, colors: BarColors) {
    val gap = SegmentGap.value * density
    val block = (length - gap * (segments - 1)) / segments
    if (block <= 0f) return
    val radius = min(across, block) / 4f
    val filled = value * block * segments
    val trailed = (trail ?: 0f) * block * segments
    for (i in 0 until segments) {
        val start = i * (block + gap)
        span(vertical, start, start + block, colors.track, radius)
        val trailedHere = (trailed - i * block).coerceIn(0f, block)
        if (trailedHere > 0f) span(vertical, start, start + trailedHere, colors.trail, radius)
        val filledHere = (filled - i * block).coerceIn(0f, block)
        if (filledHere > 0f) span(vertical, start, start + filledHere, colors.fill, radius)
    }
}

/** [from] to [to] along the bar, its whole width across: from the left, or up from the bottom when [vertical]. */
private fun DrawScope.span(vertical: Boolean, from: Float, to: Float, color: Color, radius: Float) {
    if (to <= from) return
    if (vertical) {
        drawRoundedRect(x = 0f, y = height - to, width = width.toFloat(), height = to - from, color = color, radius = radius)
    } else {
        drawRoundedRect(x = from, y = 0f, width = to - from, height = height.toFloat(), color = color, radius = radius)
    }
}

/** shadcn `gap-0.5`: the space between a segmented bar's blocks. */
private val SegmentGap: Dp = Tw.Spacing.s0_5

/** shadcn `size-16`: a radial bar's box. */
internal val ShadcnRadialProgressSize: Dp = Tw.Spacing.s16
