/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.terrain

import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.serialization.json.JsonElement

/**
 * Which surface model shades a terrain entity, as a scene named it. Sits beside the entity's
 * [TerrainComponent]; [TerrainContentSystem] resolves it through the matching provider.
 */
data class TerrainSurfaceReference(
    val provider: String,
    val version: Int,
    val payload: JsonElement,
)

/**
 * A resolved surface: a shader set built on `terrainClipmapVertexStage`, and the textures for its
 * bindings from `TERRAIN_SURFACE_FIRST_BINDING`.
 */
class TerrainSurface(
    val shaders: ShaderSet,
    val textures: Map<Int, TextureAsset> = emptyMap(),
)

/**
 * Turns a [TerrainSurfaceReference]'s payload into a [TerrainSurface], loading whatever assets
 * it names. Runs in [TerrainContentSystem]'s scope, off the frame thread.
 */
fun interface TerrainSurfaceProvider {
    suspend fun resolve(reference: TerrainSurfaceReference): TerrainSurface
}
