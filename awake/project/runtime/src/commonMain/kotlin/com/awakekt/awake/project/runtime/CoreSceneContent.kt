/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.physics.CollisionMeshSource
import com.awakekt.awake.scene.shader.ShaderEffectAssets
import com.awakekt.awake.terrain.TerrainSurfaceProvider

/**
 * The content Core's capabilities read from [SceneHostServices.content]. [loadSceneContent] fills it
 * for a project. A host that already holds this content passes its own instead, built with
 * [SceneContent.build]: an editor's Play does, since it loaded the content as the scene was edited and
 * keeps a scene running when one file in it is broken.
 *
 * A `paged_terrain`'s content is not here: only [loadSceneContent] reads it.
 */
object CoreSceneContent {
    /**
     * The triangles of the scene's `mesh` and `convex_hull` shapes, as [loadCollisionMeshes] reads them.
     * [com.awakekt.awake.scene.physics.MeshColliderSystem] needs a mesh for every such collider it
     * meets, so a host that supplies its own leaves out the colliders it has none for.
     */
    val CollisionMeshes: SceneContentKey<CollisionMeshSource> = SceneContentKey("collision meshes")

    /** The particle emitters' sprite images by path, as `loadParticleSprites` reads them. */
    val ParticleSprites: SceneContentKey<Map<String, TextureAsset>> = SceneContentKey("particle sprites")

    /** The scene's shader documents and their images, as `loadShaderEffects` reads them. */
    val ShaderEffects: SceneContentKey<ShaderEffectAssets> = SceneContentKey("shader effects")

    /**
     * The providers a `terrain`'s `surface` can name, by provider id. A project's content holds the
     * layered-terrain kit's, under `awake.terrain.layers`, reading the project's files. A terrain whose
     * provider is missing draws with the built-in terrain shading, and its scene keeps the `surface`.
     */
    val TerrainSurfaces: SceneContentKey<Map<String, TerrainSurfaceProvider>> = SceneContentKey("terrain surfaces")
}
