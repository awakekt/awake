/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.resolve
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.rendering.terrain.TerrainSurface
import com.awakekt.awake.scene.rendering.terrain.TerrainSurfaceProvider
import com.awakekt.awake.scene.rendering.terrain.TerrainSurfaceReference
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The provider id a scene's terrain `surface` names to be drawn by this kit. */
const val TERRAIN_LAYERS_PROVIDER: String = "awake.terrain.layers"

/**
 * What a scene's terrain `surface.payload` holds for [TERRAIN_LAYERS_PROVIDER].
 *
 * @property palette Project path of the `*.terrainpalette.json`. Its layer images resolve relative
 * to it.
 * @property control Project path of the `*.terrainctl`, aligned with the terrain's heightmap.
 * @property lightmap Project path of an optional `*.terrainlight`, aligned with [control].
 */
@Serializable
data class TerrainLayersPayload(
    val palette: String,
    val control: String,
    val lightmap: String? = null,
)

/**
 * Loads a layered surface from [assets]: the palette, each layer's albedo and height image, and
 * the control map. Install it under [TERRAIN_LAYERS_PROVIDER]:
 *
 * ```kotlin
 * TerrainContentSystem(host, scope, providers = mapOf(TERRAIN_LAYERS_PROVIDER to TerrainLayersSurfaceProvider(assets)))
 * ```
 *
 * @param assets Where the palette, images and control map are read from, by project path.
 * @param decodeImage Turns image file bytes into RGBA8; the default uses the platform decoder.
 */
class TerrainLayersSurfaceProvider(
    private val assets: AssetSource,
    private val decodeImage: suspend (ByteArray) -> TextureAsset = ::decodeRgba8,
) : TerrainSurfaceProvider {

    override suspend fun resolve(reference: TerrainSurfaceReference): TerrainSurface {
        require(reference.provider == TERRAIN_LAYERS_PROVIDER) {
            "This provider draws '$TERRAIN_LAYERS_PROVIDER' surfaces, not '${reference.provider}'."
        }
        val payload = payloadJson.decodeFromJsonElement(TerrainLayersPayload.serializer(), reference.payload)
        val palettePath = AssetPath(payload.palette)
        val palette = TerrainLayerPaletteCodec.decode(read(palettePath))
        val albedo = palette.layers.map { decodeImage(read(palettePath.resolve(it.albedo))) }
        val heights = palette.layers.map { layer -> layer.height?.let { decodeImage(read(palettePath.resolve(it))) } }
        val control = TerrainControlMapCodec.decode(read(AssetPath(payload.control)))
        val lightmap = payload.lightmap?.let { TerrainLightmapCodec.decode(read(AssetPath(it))) } ?: TerrainLightmap.Neutral
        return terrainLayersSurface(palette, packLayerArray(albedo, heights), control, lightmap)
    }

    private suspend fun read(path: AssetPath): ByteArray =
        assets.read(path).getOrElse { throw IllegalStateException("Terrain layers could not read '$path'.", it) }

    private companion object {
        val payloadJson = Json { ignoreUnknownKeys = true }
    }
}

private suspend fun decodeRgba8(bytes: ByteArray): TextureAsset {
    val bitmap = createBitmap(bytes)
    return TextureAsset(bitmap.toRgba8Bytes(), bitmap.width, bitmap.height)
}
