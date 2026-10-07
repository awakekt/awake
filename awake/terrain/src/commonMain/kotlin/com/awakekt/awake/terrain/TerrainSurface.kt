/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.terrain

import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.serialization.json.JsonElement

/**
 * Which surface model shades a terrain, as a scene or project named it. The scene binding keeps it
 * beside the terrain entity's component and resolves it through the provider registered for
 * [provider].
 *
 * @property provider Unique provider key identifying the terrain surface generator.
 * @property version Payload schema version number.
 * @property payload Provider-specific configuration payload in JSON format.
 */
data class TerrainSurfaceReference(
    val provider: String,
    val version: Int,
    val payload: JsonElement,
)

/**
 * A resolved surface: a shader set built on `terrainClipmapVertexStage`, and the textures for its
 * bindings from `TERRAIN_SURFACE_FIRST_BINDING`.
 *
 * @property shaders Shader pipeline stages used to render the terrain surface.
 * @property textures Texture assets bound to shader slots for the surface.
 */
class TerrainSurface(
    val shaders: ShaderSet,
    val textures: Map<Int, TextureAsset> = emptyMap(),
)

/**
 * Turns a [TerrainSurfaceReference]'s payload into a [TerrainSurface], loading whatever assets
 * it names. The caller runs it off the frame thread.
 */
fun interface TerrainSurfaceProvider {
    /**
     * Resolves the given surface [reference] into a fully loaded [TerrainSurface].
     *
     * @param reference The terrain surface reference to resolve.
     * @return The asynchronously resolved [TerrainSurface].
     */
    suspend fun resolve(reference: TerrainSurfaceReference): TerrainSurface
}
