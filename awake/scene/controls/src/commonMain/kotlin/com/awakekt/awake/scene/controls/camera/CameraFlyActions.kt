/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.camera

import com.awakekt.awake.core.input.ActionInputSource
import com.awakekt.awake.core.input.AxisAction
import com.awakekt.awake.core.input.ButtonAction
import com.awakekt.awake.core.input.InputActionDefinition
import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.core.input.Key
import kotlin.math.sign

/**
 * The input actions a free-fly camera moves by, and what triggers them unless the host binds them
 * itself, as [CameraGesturePolicy.flyActions] does.
 */
object CameraFlyActions {
    /** The axis that moves the eye along the view, up being forward and right being right. */
    const val FLY = "fly"

    /** The axis that moves the eye up and down in world space, up being up. */
    const val RISE = "rise"

    /** The button that flies faster while active. */
    const val FAST = "fast"

    /** W/S/A/D fly along the view, E and Q rise and sink, and Shift flies faster while held. */
    val defaults: List<InputActionDefinition> = listOf(
        AxisAction(FLY, up = setOf(Key.W), down = setOf(Key.S), left = setOf(Key.A), right = setOf(Key.D)),
        AxisAction(RISE, up = setOf(Key.E), down = setOf(Key.Q)),
        ButtonAction(FAST, keys = setOf(Key.Shift)),
    )
}

/**
 * Where a free-fly camera is asked to go this frame, read from [definitions], which name
 * [CameraFlyActions.FLY], [CameraFlyActions.RISE] and [CameraFlyActions.FAST].
 *
 * Each direction is -1, 0 or 1, so the caller normalises the three together and two keys at once
 * move no faster than one.
 */
internal class CameraFlyInput(definitions: List<InputActionDefinition>) {
    private val actions = InputActions(definitions)

    var ahead = 0f
        private set
    var across = 0f
        private set
    var rise = 0f
        private set
    var fast = false
        private set

    val moving: Boolean get() = ahead != 0f || across != 0f || rise != 0f

    fun read(source: ActionInputSource) {
        actions.beginFrame()
        actions.read(source)
        ahead = sign(actions.axisY(CameraFlyActions.FLY))
        across = sign(actions.axisX(CameraFlyActions.FLY))
        rise = sign(actions.axisY(CameraFlyActions.RISE))
        fast = actions.isActive(CameraFlyActions.FAST)
    }
}
