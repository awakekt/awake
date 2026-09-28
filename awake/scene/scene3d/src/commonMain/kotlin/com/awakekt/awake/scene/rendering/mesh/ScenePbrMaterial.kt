/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

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
 * @property textureAnimation How a textured material's texture moves; `null` is a still texture.
 */
@Serializable
@SerialName("pbr_material")
data class ScenePbrMaterial(
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
    val baseColorFactor: SceneColor = SceneColor.White,
    val emissiveFactor: SceneColor = SceneColor.Transparent,
    val alphaMode: AlphaMode = AlphaMode.Opaque,
    val alphaCutoff: Float = 0.5f,
    val textureAnimation: SceneTextureAnimation? = null,
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
        textureAnimation?.let { addAll(it.validate("$path.textureAnimation")) }
    }
}

/**
 * A textured material's texture animation: a frame sheet of [columns] x [rows] cells played in
 * reading order (left to right, then top to bottom of the image), and a UV scroll.
 *
 * @property columns Frame-sheet columns.
 * @property rows Frame-sheet rows.
 * @property frameCount Frames played, from the first; 0 means every cell.
 * @property framesPerSecond Playback rate; 0 holds the first frame.
 * @property scrollU UV units per second along U.
 * @property scrollV UV units per second along V, toward the bottom of the image.
 */
@Serializable
data class SceneTextureAnimation(
    val columns: Int = 1,
    val rows: Int = 1,
    val frameCount: Int = 0,
    val framesPerSecond: Float = 0f,
    val scrollU: Float = 0f,
    val scrollV: Float = 0f,
) {
    /** Every cell when [frameCount] is 0. */
    val frames: Int get() = if (frameCount == 0) columns * rows else frameCount

    fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (columns < 1 || rows < 1) add(SceneValidationIssue(path, "columns and rows must be at least 1"))
        if (frameCount < 0 || frameCount > columns * rows) {
            add(SceneValidationIssue(path, "frameCount must be within 0..columns * rows"))
        }
        if (framesPerSecond < 0f || !framesPerSecond.isFinite()) {
            add(SceneValidationIssue(path, "framesPerSecond must be finite and >= 0"))
        }
        if (!scrollU.isFinite() || !scrollV.isFinite()) add(SceneValidationIssue(path, "the UV scroll must be finite"))
    }
}
