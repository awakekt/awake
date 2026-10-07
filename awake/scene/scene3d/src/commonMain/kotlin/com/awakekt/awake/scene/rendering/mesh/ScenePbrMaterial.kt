/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable PBR material parameter component.
 *
 * @property metallic Metallic factor between 0.0 (dielectric) and 1.0 (metallic).
 * @property roughness Roughness factor between 0.0 (smooth) and 1.0 (rough).
 * @property baseColorFactor Surface base color tint factor.
 * @property emissiveFactor Surface emissive color tint factor.
 * @property alphaMode Alpha blending or masking mode for surface transparency.
 * @property alphaCutoff Alpha cutoff threshold when [alphaMode] is [AlphaMode.Mask].
 */
@Serializable
@SerialName("pbr_material")
data class ScenePbrMaterial(
    @PropertyRange(min = 0.0, max = 1.0) val metallic: Float = 0f,
    @PropertyRange(min = 0.0, max = 1.0) val roughness: Float = 0.5f,
    val baseColorFactor: SceneColor = SceneColor.White,
    val emissiveFactor: SceneColor = SceneColor.Transparent,
    val alphaMode: AlphaMode = AlphaMode.Opaque,
    @PropertyRange(min = 0.0, max = 1.0) val alphaCutoff: Float = 0.5f,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (metallic !in 0f..1f) {
            add(SceneValidationIssue(path, "pbrMaterial.metallic must be within 0..1"))
        }
        if (roughness !in 0f..1f) {
            add(SceneValidationIssue(path, "pbrMaterial.roughness must be within 0..1"))
        }
        if (alphaCutoff !in 0f..1f) {
            add(SceneValidationIssue(path, "pbrMaterial.alphaCutoff must be within 0..1"))
        }
    }
}
