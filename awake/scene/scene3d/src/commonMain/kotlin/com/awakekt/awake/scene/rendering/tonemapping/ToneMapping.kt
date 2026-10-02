/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.tonemapping

/**
 * ToneMapping component — how bright the lit scene is shown.
 *
 * Matches Godot's `Environment.tonemap_exposure`. The curve itself is fixed: Khronos PBR Neutral,
 * which keeps colours below its shoulder as authored and rolls highlights off toward white.
 *
 * @property exposure What lit radiance is multiplied by before tone mapping, finite and above 0.
 *   At 1 a white surface facing a light of intensity 1 shows near white; 2 is one stop brighter.
 */
data class ToneMapping(
    var exposure: Float = 1f,
)
