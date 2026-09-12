/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.core.color.Color

/**
 * AmbientLight component — baseline omnidirectional illumination applied to all fragments.
 *
 * Matches Godot's `ambient_light_*` / Unreal's `SkyLight`.
 *
 * @property intensity Ambient lighting baseline intensity (0 = no fill, 1 = full-bright).
 * @property color Ambient lighting color filter.
 */
data class AmbientLight(
    var intensity: Float = 0.2f,
    var color: Color = Color.White,
)
