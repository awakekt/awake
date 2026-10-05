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
 * @property transparent Draws in the transparent pass: alpha-blended by the material's alpha,
 *   depth-tested but not depth-written, sorted back to front. See [MeshRenderer.transparent].
 * @property additive With [transparent], adds its colour to what is behind it: glows, fire,
 *   light shafts. See [MeshRenderer.additive].
 * @property billboard Turns the mesh to face the camera every frame, keeping its position and
 *   scale: a glow or flare authored as a flat quad. See [MeshRenderer.billboard].
 */
@Serializable
@SerialName("mesh_renderer")
data class SceneMeshRenderer(
    val mesh: String,
    val material: String,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("cull_mode", "cullMode")
    val cullMode: CullMode = CullMode.None,
    val transparent: Boolean = false,
    val additive: Boolean = false,
    val billboard: Boolean = false,
) : SceneComponent {
    /** GPU face culling mode enumeration. */
    enum class CullMode {
        /** No polygon faces are culled (double-sided rendering). */
        None,

        /** Back-facing polygons are culled. */
        Back,

        /** Front-facing polygons are culled. */
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
