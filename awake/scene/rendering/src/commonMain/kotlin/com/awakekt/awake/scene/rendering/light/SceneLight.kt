/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneVec3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable light source component.
 *
 * @property color Light RGB color vector.
 * @property intensity Radiance/luminance multiplier.
 * @property type Light source emission type.
 * @property direction World-space emission direction vector for directional lights.
 * @property range Maximum falloff distance for point lights.
 */
@Serializable
@SerialName("light")
data class SceneLight(
    val color: SceneVec3 = SceneVec3(1f, 1f, 1f),
    val intensity: Float = 1f,
    val type: Type = Type.Point,
    val direction: SceneVec3 = SceneVec3(0.4f, 0.8f, 0.4f),
    val range: Float = 10f,
) : SceneComponent {
    /** Light source emission type enumeration. */
    @Serializable
    enum class Type {
        /** Infinite directional sun light. */
        Directional,

        /** Omnidirectional point light source. */
        Point,
    }
}
