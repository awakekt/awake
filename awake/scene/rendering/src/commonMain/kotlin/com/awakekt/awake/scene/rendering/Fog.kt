/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color

/**
 * Fog component — defines distance-based exponential fog in the scene.
 *
 * Decoupled from [Environment] so atmospheric distance haze can be placed, toggled, or modified
 * independently of skybox or ambient lighting. Matches Godot's `Environment.fog_enabled` /
 * Unreal's `ExponentialHeightFog`.
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
