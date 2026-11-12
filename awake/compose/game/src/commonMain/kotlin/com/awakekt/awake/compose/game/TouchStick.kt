/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.game

import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.ImageFill
import com.awakekt.awake.compose.ui.graphics.drawImageFill
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.layout.onSizeChanged
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color

private class StickOwner(val state: TouchStickState) : TouchOwner {
    override val owner: Long get() = state.pointer

    override fun press(id: Long, px: Float, py: Float) = state.press(id, px, py)

    override fun move(px: Float, py: Float) = state.move(px, py)

    override fun release() = state.release()
}

/**
 * A virtual stick for a thumb, with the canvas Joystick's output so a consumer swaps one for the
 * other: [onChange] gets x and y from -1 to 1, y down as on screen, and (0, 0) on release or cancel.
 * Read the same values from [state] anywhere.
 *
 * The control is the zone a thumb may land in; give it a size. [TouchStickMode.Fixed] centres the
 * pad; the other modes put it where the thumb lands. The first finger owns the stick until it lifts,
 * so a second finger elsewhere neither moves nor resets it. Lengths are dp; nothing allocates while
 * a finger is held.
 *
 * @param modifier Sizes and places the zone a thumb may land in.
 * @param state The live state; pass your own to read the stick anywhere.
 * @param mode Where the pad sits.
 * @param radius How far the knob reaches from the pad's centre; 0 takes half the shorter side when
 * fixed and [DEFAULT_ZONE_RADIUS] otherwise.
 * @param deadZone The share of [radius] that reports nothing, from 0 up to but not including 1.
 * @param response 1 in proportion, 2 or 3 for finer control near the centre.
 * @param directions Whether any angle is reported, or only the nearest of four or eight.
 * @param snapBack Whether letting go centres the stick; false holds the last deflection.
 * @param restX Where across its zone an untouched floating pad waits, 0 left to 1 right.
 * @param restY Where down its zone an untouched floating pad waits, 0 top to 1 bottom.
 * @param look The base and knob fills.
 * @param onChange Called with the stick's x and y whenever they change, and with (0, 0) on release.
 */
context(_: Composer)
fun TouchStick(
    modifier: Modifier = Modifier,
    state: TouchStickState = remember { TouchStickState() },
    mode: TouchStickMode = TouchStickMode.Fixed,
    radius: Dp = 0.dp,
    deadZone: Float = 0f,
    response: Int = 1,
    directions: TouchStickDirections = TouchStickDirections.Analog,
    snapBack: Boolean = true,
    restX: Float = 0.5f,
    restY: Float = 0.5f,
    look: TouchLook = remember { TouchLook() },
    onChange: ((x: Float, y: Float) -> Unit)? = null,
) {
    require(deadZone >= 0f && deadZone < 1f) { "deadZone must be at least 0 and below 1" }
    require(response in 1..3) { "response must be 1, 2 or 3" }
    val density = LocalDensity.current
    val owner = remember(state) { StickOwner(state) }
    state.mode = mode
    state.deadZone = deadZone
    state.power = response
    state.directions = directions
    state.snapBack = snapBack
    state.onChange = onChange
    state.reach = reachPx(radius, mode, state, density)
    Spacer(
        modifier
            .onSizeChanged { w, h ->
                state.width = w.toFloat()
                state.height = h.toFloat()
                state.reach = reachPx(radius, mode, state, density)
            }
            .touchOwner(owner)
            .drawBehind { drawStick(state, look, restX, restY) },
    )
}

/** The default reach of a floating or dynamic pad, in dp. */
const val DEFAULT_ZONE_RADIUS: Float = 60f

private fun reachPx(radius: Dp, mode: TouchStickMode, state: TouchStickState, density: Float): Float = when {
    radius.value > 0f -> radius.value * density
    mode == TouchStickMode.Fixed -> minOf(state.width, state.height) / 2f
    else -> DEFAULT_ZONE_RADIUS * density
}

private fun DrawScope.drawStick(state: TouchStickState, look: TouchLook, restX: Float, restY: Float) {
    val reach = state.reach
    if (reach <= 0f) return
    val cx = state.padX(restX)
    val cy = state.padY(restY)
    disc(look.base, look.baseColor, cx, cy, reach)
    disc(look.knob, look.knobColor, cx + state.knobX, cy + state.knobY, reach * KNOB_SHARE)
}

internal fun DrawScope.disc(fill: ImageFill?, color: Color, cx: Float, cy: Float, radius: Float) {
    val size = radius * 2f
    if (fill != null) {
        drawImageFill(fill, cx - radius, cy - radius, size, size)
    } else {
        drawRoundedRect(cx - radius, cy - radius, size, size, color, radius)
    }
}

private const val KNOB_SHARE = 0.4f
