/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color

val DefaultHorizonColor = Color(r = 0.72f, g = 0.80f, b = 0.88f, a = 1f)
val DefaultZenithColor = Color(r = 0.20f, g = 0.38f, b = 0.68f, a = 1f)
val DefaultFogColor = Color(r = 0.55f, g = 0.62f, b = 0.70f, a = 1f)

/**
 * World environment component — atmospheric sky appearance, ambient fill, and distance fog.
 *
 * Matches Godot's `WorldEnvironment` resource: it owns the SKY (gradient, show/hide), ambient
 * light intensity, and fog. The SUN (direction, intensity, color, shadow toggle) belongs to the
 * scene's `Light(type=Directional)` entity, same as Godot's `DirectionalLight3D`.
 *
 * @property showEnvironment Whether procedural skybox and atmospheric horizon are rendered.
 * @property horizonColor Atmospheric horizon gradient color.
 * @property zenithColor Atmospheric upper sky (zenith) color.
 * @property fogDensity Distance-based exponential fog density factor.
 * @property fogColor Distance fog color.
 * @property ambientLight Ambient lighting baseline intensity (0 = no fill, 1 = full-bright).
 */
data class Environment(
    var showEnvironment: Boolean = true,
    var horizonColor: Color = DefaultHorizonColor,
    var zenithColor: Color = DefaultZenithColor,
    var fogDensity: Float = 0.001f,
    var fogColor: Color = DefaultFogColor,
    var ambientLight: Float = 0.2f,
)
