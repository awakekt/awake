/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.fog

import com.awakekt.awake.core.color.Color

val DefaultFogColor = Color(r = 0.55f, g = 0.62f, b = 0.70f, a = 1f)

/**
 * Fog component — defines distance-based exponential fog in the scene.
 *
 * Matches Godot's `Environment.fog_enabled` / Unreal's `ExponentialHeightFog`.
 *
 * @property enabled Whether distance fog is applied during scene shading.
 * @property density Distance-based exponential fog density factor.
 * @property color Distance fog color.
 */
data class Fog(
    var enabled: Boolean = true,
    var density: Float = 0.001f,
    var color: Color = DefaultFogColor,
)
