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
val DefaultSunColor = Color(r = 1f, g = 0.98f, b = 0.95f, a = 1f)

/**
 * World environment component for atmospheric sky, ambient lighting, shadows, and fog.
 *
 * Attaching this component to an entity in the scene defines environment rendering parameters.
 *
 * @property sunAzimuthDeg Directional sun azimuth angle in degrees (0..360°).
 * @property sunElevationDeg Directional sun elevation angle in degrees (-90..90°).
 * @property sunIntensity Radiance multiplier for the directional sunlight.
 * @property sunColor Color of the directional sunlight.
 * @property showEnvironment Whether procedural skybox and atmospheric horizon are rendered.
 * @property horizonColor Atmospheric horizon gradient color.
 * @property zenithColor Atmospheric upper sky (zenith) color.
 * @property fogDensity Distance-based exponential fog density factor.
 * @property fogColor Distance fog color.
 * @property ambientLight Ambient lighting baseline intensity.
 * @property shadowsEnabled Whether scene shadows are computed and rendered.
 */
data class Environment(
    var sunAzimuthDeg: Float = 45f,
    var sunElevationDeg: Float = 60f,
    var sunIntensity: Float = 1.0f,
    var sunColor: Color = DefaultSunColor,
    var showEnvironment: Boolean = true,
    var horizonColor: Color = DefaultHorizonColor,
    var zenithColor: Color = DefaultZenithColor,
    var fogDensity: Float = 0.001f,
    var fogColor: Color = DefaultFogColor,
    var ambientLight: Float = 0.2f,
    var shadowsEnabled: Boolean = true,
)
