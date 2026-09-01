/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.controls

import io.github.awakelab.awake.core.math.CameraMathUtils
import io.github.awakelab.awake.scene.controls.camera.CameraMode

/**
 * Data-driven configuration for camera mouse, keyboard, and gesture controls.
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
)
