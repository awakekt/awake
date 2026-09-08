/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Serializable mesh renderer component linking a geometry mesh and material.
 *
 * @property mesh Mesh asset resource identifier or primitive shape name.
 * @property material Material asset resource identifier.
 * @property cullMode GPU face culling mode.
 */
@Serializable
@SerialName("mesh_renderer")
data class SceneMeshRenderer(
    val mesh: String,
    val material: String,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("cull_mode", "cullMode")
    val cullMode: CullMode = CullMode.None,
) : SceneComponent {
    /** GPU face culling mode enumeration. */
    enum class CullMode {
        None,
        Back,
        Front,
    }

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (mesh.isBlank()) {
            add(SceneValidationIssue(path, "meshRenderer.mesh must not be blank"))
        }
        if (material.isBlank()) {
            add(SceneValidationIssue(path, "meshRenderer.material must not be blank"))
        }
    }
}
