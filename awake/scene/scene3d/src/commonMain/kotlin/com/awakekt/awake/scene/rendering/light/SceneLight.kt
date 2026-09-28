/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.render.passes.DEFAULT_SHADOW_DISTANCE
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable light source component.
 *
 * @property color Light color.
 * @property intensity Radiance/luminance multiplier.
 * @property type Light source emission type.
 * @property direction World-space emission direction vector for directional lights.
 * @property range Maximum falloff distance for point lights.
 * @property shadowDistance How far from the camera a directional light's shadows reach, in world units.
 */
@Serializable
@SerialName("light")
data class SceneLight(
    val color: SceneColor = SceneColor.White,
    val intensity: Float = 1f,
    val type: Type = Type.Point,
    val direction: SceneVec3 = SceneVec3(0.4f, 0.8f, 0.4f),
    val range: Float = 10f,
    /** Whether this directional light casts shadows. Defaults true so existing scenes
     * (which didn't have this field) keep their shadows after loading. */
    val shadowsEnabled: Boolean = true,
    val shadowDistance: Float = DEFAULT_SHADOW_DISTANCE,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (!shadowDistance.isFinite() || shadowDistance <= 0f) {
            add(SceneValidationIssue(path, "light.shadowDistance must be finite and > 0; was $shadowDistance"))
        }
    }

    /** Light source emission type enumeration. */
    @Serializable
    enum class Type {
        /** Infinite directional sun light. */
        Directional,

        /** Omnidirectional point light source. */
        Point,
    }
}
