/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import com.awakekt.awake.core.color.Color

val DefaultHorizonColor = Color(r = 0.72f, g = 0.80f, b = 0.88f, a = 1f)
val DefaultZenithColor = Color(r = 0.20f, g = 0.38f, b = 0.68f, a = 1f)

/**
 * Skybox component — defines the atmospheric sky gradient or sky appearance behind the scene.
 *
 * Matches Godot's `Sky` resource / Unreal's `SkyAtmosphere`.
 *
 * @property enabled Whether procedural skybox and atmospheric horizon are rendered.
 * @property horizonColor Atmospheric horizon gradient color.
 * @property zenithColor Atmospheric upper sky (zenith) color.
 */
data class Skybox(
    var enabled: Boolean = true,
    var horizonColor: Color = DefaultHorizonColor,
    var zenithColor: Color = DefaultZenithColor,
    var cubemapPath: String? = null,
    var exposure: Float = 1.0f,
    var type: Type = Type.Procedural,
) {
    enum class Type {
        Procedural,
        Cubemap,
        SolidColor,
    }
}
