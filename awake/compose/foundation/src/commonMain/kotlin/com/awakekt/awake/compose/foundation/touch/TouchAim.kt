/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.foundation.touch

import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp

private class AimOwner(val state: TouchAimState) : TouchOwner {
    override val owner: Long get() = state.pointer

    override fun press(id: Long, px: Float, py: Float) = state.press(id, px, py)

    override fun move(px: Float, py: Float) = state.move(px, py)

    override fun release() = state.release()
}

/**
 * Drag to aim, with the canvas Aim's output so a consumer swaps one for the other: the aim is the
 * drag from where the press landed, as a share of [radius], y down as on screen. [onAim] gets it as
 * the finger moves, and [onAimEnd] the aim it was let go with (zero for a drag that stayed inside
 * [deadZone], so a tap fires at whatever the game picks) once, on release or cancel.
 *
 * The first finger owns the control until it lifts. Read the same values from [state] anywhere.
 *
 * @param modifier Sizes and places the zone a thumb may drag in.
 * @param state The live state; pass your own to read the aim anywhere.
 * @param radius How far, in dp, a drag goes for a full-length aim; 0 takes [DEFAULT_AIM_RADIUS].
 * @param deadZone The share of [radius] around the press that is no aim, from 0 up to but not
 * including 1.
 * @param ground False for a direction, always full length once past the dead zone; true for a
 * ground target, as far out as the drag.
 * @param look The press-point ring and thumb dot fills.
 * @param onAim Called with the aim whenever the finger moves.
 * @param onAimEnd Called once with the aim the finger was let go with.
 */
context(_: Composer)
fun TouchAim(
    modifier: Modifier = Modifier,
    state: TouchAimState = remember { TouchAimState() },
    radius: Dp = 0.dp,
    deadZone: Float = DEFAULT_AIM_DEAD_ZONE,
    ground: Boolean = false,
    look: TouchLook = remember { TouchLook() },
    onAim: ((x: Float, y: Float) -> Unit)? = null,
    onAimEnd: ((x: Float, y: Float) -> Unit)? = null,
) {
    require(deadZone >= 0f && deadZone < 1f) { "deadZone must be at least 0 and below 1" }
    val density = LocalDensity.current
    val owner = remember(state) { AimOwner(state) }
    state.reach = (if (radius.value > 0f) radius.value else DEFAULT_AIM_RADIUS) * density
    state.deadZone = deadZone
    state.ground = ground
    state.onAim = onAim
    state.onAimEnd = onAimEnd
    Spacer(
        modifier
            .touchOwner(owner)
            .drawBehind {
                if (state.isAiming) {
                    // The press point as the base, the thumb as the knob: where the shot points.
                    disc(look.base, look.baseColor, state.originX, state.originY, state.reach)
                    disc(look.knob, look.knobColor, state.thumbX, state.thumbY, state.reach * AIM_KNOB_SHARE)
                }
            },
    )
}

/** How far a full-length aim drags, in dp, by default. */
const val DEFAULT_AIM_RADIUS: Float = 120f

/** The share of the reach that is no aim, by default. */
const val DEFAULT_AIM_DEAD_ZONE: Float = 0.2f

private const val AIM_KNOB_SHARE = 0.2f
