/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.awakekt.awake.core.input.StickVector
import com.awakekt.awake.core.input.TouchStickMath

/**
 * A [TouchAim]'s live state, owned by the caller. [x] and [y] are the aim while a finger drags, and
 * stay at the aim it was let go with until the next press, as a fired shot reads them. Positions are
 * pixels from the control's top-left; one vector is reused, so a held finger allocates nothing.
 */
class TouchAimState {
    private val out = StickVector()

    /** The aim from -1 to 1 to the right; 0 inside the dead zone. */
    val x: Float get() = out.x

    /** The vertical aim from -1 to 1; up is negative, as on screen. */
    val y: Float get() = out.y

    /** Whether a finger holds the control and is dragging it. */
    val isAiming: Boolean get() = pointer != TouchStickState.NONE

    internal var pointer: Long = TouchStickState.NONE
        private set
    internal var originX = 0f
        private set
    internal var originY = 0f
        private set
    internal var thumbX = 0f
        private set
    internal var thumbY = 0f
        private set

    // Settings, set every build by TouchAim, in pixels.
    internal var reach = 0f
    internal var deadZone = 0f
    internal var ground = false
    internal var onAim: ((Float, Float) -> Unit)? = null
    internal var onAimEnd: ((Float, Float) -> Unit)? = null

    internal fun press(id: Long, px: Float, py: Float): Boolean {
        if (pointer != TouchStickState.NONE) return false
        pointer = id
        originX = px
        originY = py
        thumbX = px
        thumbY = py
        out.set(0f, 0f)
        return true
    }

    internal fun move(px: Float, py: Float) {
        thumbX = px
        thumbY = py
        TouchStickMath.aim(px - originX, py - originY, reach, deadZone, ground, out)
        onAim?.invoke(out.x, out.y)
    }

    /** Lets go, as a release or a cancel does; the aim it had is reported once and kept. */
    internal fun release() {
        if (pointer == TouchStickState.NONE) return
        pointer = TouchStickState.NONE
        onAimEnd?.invoke(out.x, out.y)
    }
}
