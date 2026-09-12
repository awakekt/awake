/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color

/**
 * Skybox component — defines the atmospheric sky gradient or sky appearance behind the scene.
 *
 * Decoupled from [Environment] so sky rendering can be authored, configured, or toggled
 * independently of fog and ambient lighting. Matches Godot's `Sky` resource / Unreal's `SkyAtmosphere`.
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
