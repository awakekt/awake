/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.schema.PropertyRange
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
 *   light shafts. A textured mesh adds unlit unless its `pbr_material` sets
 *   [ScenePbrMaterial.litWhenAdditive]. See [MeshRenderer.additive].
 * @property billboard Turns the mesh to face the camera every frame, keeping its position and
 *   scale: a glow or flare authored as a flat quad. See [MeshRenderer.billboard].
 * @property castsShadows Draws the mesh into the shadow maps, in view or off screen. False for a
 *   decal, a ground card or distant filler. A document without the field casts, as every mesh did
 *   before it existed. See [MeshRenderer.castsShadows].
 * @property maxDrawDistance Past this distance from the camera, the mesh draws nowhere, its shadow
 *   included. `0`, the default, draws it at any distance. See [MeshRenderer.maxDrawDistance].
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
    val castsShadows: Boolean = true,
    @PropertyRange(min = 0.0) val maxDrawDistance: Float = 0f,
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

    /** [SceneMeshRenderer] as it was before [maxDrawDistance]: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        mesh: String,
        material: String,
        cullMode: CullMode = CullMode.None,
        transparent: Boolean = false,
        additive: Boolean = false,
        billboard: Boolean = false,
        castsShadows: Boolean = true,
    ) : this(mesh, material, cullMode, transparent, additive, billboard, castsShadows, 0f)

    /** [copy] as it was before [maxDrawDistance]: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        mesh: String = this.mesh,
        material: String = this.material,
        cullMode: CullMode = this.cullMode,
        transparent: Boolean = this.transparent,
        additive: Boolean = this.additive,
        billboard: Boolean = this.billboard,
        castsShadows: Boolean = this.castsShadows,
    ): SceneMeshRenderer = SceneMeshRenderer(mesh, material, cullMode, transparent, additive, billboard, castsShadows, maxDrawDistance)

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (mesh.isBlank()) {
            add(SceneValidationIssue(path, "meshRenderer.mesh must not be blank"))
        }
        if (material.isBlank()) {
            add(SceneValidationIssue(path, "meshRenderer.material must not be blank"))
        }
        if (!(maxDrawDistance >= 0f) || maxDrawDistance.isInfinite()) {
            add(SceneValidationIssue(path, "meshRenderer.maxDrawDistance must be a finite distance of 0 or more, got $maxDrawDistance"))
        }
    }
}
