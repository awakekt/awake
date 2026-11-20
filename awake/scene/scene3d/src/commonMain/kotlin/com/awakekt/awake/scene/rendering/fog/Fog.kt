/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.fog

import com.awakekt.awake.core.color.Color

/**
 * Default color applied for distance fog in 3D scenes.
 */
val DefaultFogColor = Color(r = 0.55f, g = 0.62f, b = 0.70f, a = 1f)

/** Where a [FogMode.Linear] fog starts by default, in metres from the eye. */
const val DEFAULT_FOG_START = 0f

/** Where a [FogMode.Linear] fog reaches its full colour by default, in metres from the eye. */
const val DEFAULT_FOG_END = 1000f

/**
 * Fog component — defines distance fog in the scene, exponential by [density] or linear from
 * [start] to [end]. Every field may be written at runtime; the next frame fogs with it.
 *
 * @property enabled Whether distance fog is applied during scene shading.
 * @property density Distance-based exponential fog density factor; unused by [FogMode.Linear].
 * @property color Distance fog color.
 * @property mode How fog grows with distance; [FogMode.Exponential] fogs exactly as before.
 * @property start Where a linear fog starts, in metres from the eye; unused by exponential fog.
 * @property end Where a linear fog reaches [color], in metres from the eye; beyond [start].
 */
data class Fog(
    var enabled: Boolean = true,
    var density: Float = 0.001f,
    var color: Color = DefaultFogColor,
    var mode: FogMode = FogMode.Exponential,
    var start: Float = DEFAULT_FOG_START,
    var end: Float = DEFAULT_FOG_END,
) {
    init {
        requireFogRange(start, end)
    }

    /** The constructor as released before [mode], kept so compiled callers still link. */
    @Deprecated("Use the constructor with mode, start and end.", level = DeprecationLevel.HIDDEN)
    constructor(enabled: Boolean = true, density: Float = 0.001f, color: Color = DefaultFogColor) :
        this(enabled, density, color, FogMode.Exponential, DEFAULT_FOG_START, DEFAULT_FOG_END)

    /** [copy] as released before [mode]. */
    @Deprecated("Use copy with mode, start and end.", level = DeprecationLevel.HIDDEN)
    fun copy(enabled: Boolean = this.enabled, density: Float = this.density, color: Color = this.color): Fog =
        copy(enabled, density, color, mode, start, end)
}
