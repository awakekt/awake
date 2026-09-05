/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.authoring.dsl

import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh
import io.github.awakelab.awake.render.renderer.CullMode
import io.github.awakelab.awake.scene.authoring.SceneAppDsl
import io.github.awakelab.awake.scene.rendering.mesh.MeshRenderer

/**
 * Configures a [MeshRenderer] component on this entity.
 *
 * @param mesh The mesh asset to render.
 * @param material The material to shade the mesh with.
 * @param cullMode The rasterizer triangle cull mode.
 */
fun EntityScope.meshRenderer(
    mesh: Mesh,
    material: Material,
    cullMode: CullMode = CullMode.None,
) = with(MeshRenderer(mesh, material, cullMode))

/**
 * Alias for [meshRenderer] providing concise mesh and material attachment on [EntityScope].
 *
 * @param mesh The mesh geometry asset.
 * @param material The material asset.
 * @param cullMode The triangle face cull mode.
 */
fun EntityScope.mesh(
    mesh: Mesh,
    material: Material,
    cullMode: CullMode = CullMode.None,
) = meshRenderer(mesh, material, cullMode)

/**
 * Spawns a mesh renderer entity with geometry, material, and cull mode setup on [SceneAppDsl].
 *
 * @param name The descriptive name for the mesh entity.
 * @param mesh The mesh geometry asset.
 * @param material The material asset.
 * @param cullMode The rasterizer triangle face cull mode.
 * @param block Optional additional configuration block on the [EntityScope].
 */
fun SceneAppDsl.meshEntity(
    name: String,
    mesh: Mesh,
    material: Material,
    cullMode: CullMode = CullMode.None,
    block: EntityScope.() -> Unit = {},
) {
    entity(name) {
        meshRenderer(mesh, material, cullMode)
        block()
    }
}
