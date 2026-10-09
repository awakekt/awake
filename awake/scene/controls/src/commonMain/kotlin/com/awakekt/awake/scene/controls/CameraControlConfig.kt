/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.core.input.AxisAction
import com.awakekt.awake.core.input.InputActionDefinition
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.math.CameraMathUtils
import com.awakekt.awake.scene.controls.camera.CameraFlyActions
import com.awakekt.awake.scene.controls.camera.CameraMode

/**
 * Data-driven configuration for camera mouse, keyboard, and gesture controls.
 *
 * @property mode Active camera projection and tracking mode.
 * @property orbitSensitivity Sensitivity factor applied to mouse orbit gestures in radians per pixel.
 * @property zoomRate Zoom step scaling factor applied to mouse scroll delta.
 * @property baseMoveSpeed Base keyboard translation speed in units per second.
 * @property boostMultiplier Speed multiplier applied when holding the boost modifier key.
 * @property panScale Pan scaling factor converting screen space pixel delta to world units.
 * @property minDistance Minimum zoom or orbit distance from the target in world units.
 * @property maxDistance Maximum zoom or orbit distance from the target in world units.
 * @property minPitchRad Minimum allowable pitch angle in radians.
 * @property maxPitchRad Maximum allowable pitch angle in radians.
 * @property invertYaw Whether to invert horizontal yaw rotation input.
 * @property invertPitch Whether to invert vertical pitch rotation input.
 * @property invertScroll Whether to invert mouse scroll wheel zoom direction.
 * @property allowOrbit Whether orbiting around the target is permitted.
 * @property allowZoom Whether zooming towards or away from the target is permitted.
 * @property allowPan Whether panning across the camera plane is permitted.
 * @property allowKeyboardFlight Whether keyboard translation and flight controls are active.
 * @property flyActions The input actions keyboard flight moves by, named as in [CameraFlyActions]:
 *   by default its defaults, with Space rising as well as E.
 */
data class CameraControlConfig(
    // Active Camera Mode
    val mode: CameraMode = CameraMode.ThirdPerson,

    // Sensitivities & Rates
    val orbitSensitivity: Float = 0.005f,
    val zoomRate: Float = 0.08f,
    val baseMoveSpeed: Float = 60.0f,
    val boostMultiplier: Float = 2.5f,
    val panScale: Float = 0.002f,

    // Distance & Angle Bounds
    val minDistance: Float = 3.0f,
    val maxDistance: Float = 3000.0f,
    val minPitchRad: Float = -CameraMathUtils.PITCH_LIMIT_RAD,
    val maxPitchRad: Float = CameraMathUtils.PITCH_LIMIT_RAD,

    // Invert Axes
    val invertYaw: Boolean = false,
    val invertPitch: Boolean = false,
    val invertScroll: Boolean = false,

    // Feature Toggles
    val allowOrbit: Boolean = true,
    val allowZoom: Boolean = true,
    val allowPan: Boolean = true,
    val allowKeyboardFlight: Boolean = true,
    val flyActions: List<InputActionDefinition> = PROCESSOR_FLY_ACTIONS,
)

/** [CameraFlyActions.defaults], with Space rising as well as E, as [CameraInputProcessor] always flew. */
private val PROCESSOR_FLY_ACTIONS: List<InputActionDefinition> = CameraFlyActions.defaults.map { action ->
    if (action is AxisAction && action.name == CameraFlyActions.RISE) action.copy(up = action.up + Key.Space) else action
}
