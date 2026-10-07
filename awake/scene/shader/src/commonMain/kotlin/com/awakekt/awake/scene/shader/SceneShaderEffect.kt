/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.shader

import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A shader document drawn by this node: a sky behind the scene, an overlay in front of it, or a plane
 * placed by the node's transform, as the document's `surface` says. The document is project data, a
 * `*.shader.json` file checked and compiled when the project loads; the scene only names it.
 *
 * What the document declares is checked against it when the project loads, not here, because the scene
 * file does not hold the document: a parameter it does not declare, a value of the wrong size, or a
 * texture it does not name is logged with the node, and that effect is not drawn.
 *
 * @property shader Project path of the shader document.
 * @property parameters Parameter name to its numbers, as many as the parameter's type holds: 1 for a
 * `float`, 2 to 4 for a vector, 4 for a `color`. A parameter left out takes the document's default.
 * @property textures Texture name, as the document declares it, to the project path of its image. Every
 * texture the document declares needs one.
 * @property enabled Whether the effect draws. Its clock runs either way.
 */
@Serializable
@SerialName("shader_effect")
data class SceneShaderEffect(
    val shader: String,
    val parameters: Map<String, List<Float>> = emptyMap(),
    val textures: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (shader.isBlank()) add(SceneValidationIssue(path, "shader_effect.shader names no shader document"))
        parameters.forEach { (name, values) ->
            if (name.isBlank()) add(SceneValidationIssue(path, "shader_effect.parameters has a parameter with no name"))
            if (values.isEmpty()) add(SceneValidationIssue(path, "shader_effect.parameters.$name has no value"))
            if (values.any { !it.isFinite() }) {
                add(SceneValidationIssue(path, "shader_effect.parameters.$name has a value that is not a finite number"))
            }
        }
        textures.forEach { (name, image) ->
            if (name.isBlank()) add(SceneValidationIssue(path, "shader_effect.textures has a texture with no name"))
            if (image.isBlank()) add(SceneValidationIssue(path, "shader_effect.textures.$name names no image"))
        }
    }
}
