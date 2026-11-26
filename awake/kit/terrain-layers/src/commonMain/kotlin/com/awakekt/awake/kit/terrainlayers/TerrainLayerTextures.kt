/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

private const val RGBA = 4
private const val ALPHA = 3
private const val BYTE_MAX = 255

/** Octaves the tiling byte spans: [MIN_LAYER_TILING] is 2^-2, [MAX_LAYER_TILING] is 2^6. */
private const val TILING_OCTAVES = 8f
private const val TILING_MIN_EXPONENT = -2f

/**
 * Canonical deduplication key for a terrain layer `Texture2DArray` built from resolved [layers].
 * Terrains sharing an equal key share one uploaded GPU texture array per active mip-skip level.
 */
data class TerrainLayerArrayKey(
    /** Resolved `(albedoPath, heightPath)` pairs in palette order. */
    val layers: List<LayerSourceKey>,
) {
    /** Canonical source paths for one terrain palette layer. */
    data class LayerSourceKey(
        /** Normalized project path of the layer's albedo image. */
        val albedoPath: String,
        /** Normalized project path of the optional height image, or `null` when flat. */
        val heightPath: String? = null,
    )
}

/**
 * Canonical deduplication key for a 1D [layerTable] texture built from [palette].
 */
internal data class TerrainLayerTableKey(val palette: TerrainLayerPalette)

/**
 * Packs one albedo image per palette layer into a texture array, with each layer's [heights]
 * image (its red channel) in alpha. A layer without a height image is flat: alpha 255.
 *
 * Albedo alpha is ignored rather than used for height: platform PNG decoders may premultiply it.
 * A one-layer palette is padded to two array layers, because an arrayed binding rejects one.
 */
fun packLayerArray(albedo: List<TextureAsset>, heights: List<TextureAsset?> = List(albedo.size) { null }): TextureAsset {
    require(albedo.isNotEmpty()) { "A layer array needs at least one layer." }
    require(albedo.size <= MAX_TERRAIN_LAYERS) { "${albedo.size} layers exceed $MAX_TERRAIN_LAYERS." }
    require(heights.size == albedo.size) { "${heights.size} height images for ${albedo.size} layers." }
    val width = albedo.first().width
    val height = albedo.first().height
    val targetLayers = maxOf(albedo.size, 2)
    val layerSize = width * height * RGBA
    val data = ByteArray(layerSize * targetLayers)
    albedo.forEachIndexed { layer, image ->
        copyLayerInto(data, layer, width, height, layerSize, image, heights[layer])
    }
    if (albedo.size == 1) {
        data.copyInto(data, destinationOffset = layerSize, startIndex = 0, endIndex = layerSize)
    }
    return TextureAsset(data, width, height, layerCount = targetLayers)
}

/**
 * Streaming variant of [packLayerArray] that decodes one layer at a time via [readLayer] directly
 * into the single packed array buffer, avoiding holding every decoded layer image in memory at
 * once alongside the destination array (eliminating the 2x CPU RAM peak on 4 GB devices).
 *
 * @param layerCount Number of palette layers (`1..MAX_TERRAIN_LAYERS`).
 * @param readLayer Suspends to decode `(albedo, optionalHeight)` for the given layer index.
 */
suspend fun packLayerArrayStreaming(
    layerCount: Int,
    readLayer: suspend (layer: Int) -> Pair<TextureAsset, TextureAsset?>,
): TextureAsset {
    require(layerCount >= 1) { "A layer array needs at least one layer." }
    require(layerCount <= MAX_TERRAIN_LAYERS) { "$layerCount layers exceed $MAX_TERRAIN_LAYERS." }
    val (firstAlbedo, firstHeight) = readLayer(0)
    val width = firstAlbedo.width
    val height = firstAlbedo.height
    val targetLayers = maxOf(layerCount, 2)
    val layerSize = width * height * RGBA
    val data = ByteArray(layerSize * targetLayers)
    copyLayerInto(data, 0, width, height, layerSize, firstAlbedo, firstHeight)
    if (layerCount == 1) {
        data.copyInto(data, destinationOffset = layerSize, startIndex = 0, endIndex = layerSize)
        return TextureAsset(data, width, height, layerCount = targetLayers)
    }
    for (layer in 1 until layerCount) {
        val (albedo, heightImage) = readLayer(layer)
        copyLayerInto(data, layer, width, height, layerSize, albedo, heightImage)
    }
    return TextureAsset(data, width, height, layerCount = targetLayers)
}

@Suppress("LongParameterList")
private fun copyLayerInto(
    destination: ByteArray,
    layer: Int,
    width: Int,
    height: Int,
    layerSize: Int,
    image: TextureAsset,
    heightImage: TextureAsset?,
) {
    require(image.width == width && image.height == height && image.layerCount == 1) {
        "Layer $layer is ${image.width} x ${image.height} x ${image.layerCount}; every layer must be " +
            "a single $width x $height image. Resize them when importing."
    }
    val destOffset = layer * layerSize
    image.data.copyInto(destination, destOffset, 0, layerSize)
    heightImage?.let {
        require(it.width == width && it.height == height) {
            "Layer $layer height is ${it.width} x ${it.height}; its albedo is $width x $height."
        }
    }
    val texels = width * height
    for (texel in 0 until texels) {
        destination[destOffset + texel * RGBA + ALPHA] = heightImage?.data?.get(texel * RGBA) ?: BYTE_MAX.toByte()
    }
}

/**
 * One texel per palette layer: red is [TerrainLayer.tiling] on a log scale, green is
 * [TerrainLayer.blendSharpness]. A texture rather than uniforms because a content feature owns a
 * single fixed uniform block, and a palette has any number of layers.
 */
fun layerTable(palette: TerrainLayerPalette): TextureAsset {
    val data = ByteArray(palette.layers.size * RGBA)
    palette.layers.forEachIndexed { index, layer ->
        data[index * RGBA] = encodeTiling(layer.tiling).toByte()
        data[index * RGBA + 1] = (layer.blendSharpness * BYTE_MAX).roundToInt().coerceIn(0, BYTE_MAX).toByte()
        data[index * RGBA + ALPHA] = BYTE_MAX.toByte()
    }
    // Single-level lookup table: mip-skip would shrink the 1D palette width and scramble layer parameters.
    return TextureAsset(data, palette.layers.size, 1, filtering = TextureFiltering.Nearest)
}

/** The byte the shader turns back into a tiling with `2^(byte / 255 * 8 - 2)`. */
internal fun encodeTiling(tiling: Float): Int {
    val clamped = tiling.coerceIn(MIN_LAYER_TILING, MAX_LAYER_TILING)
    return ((log2(clamped) - TILING_MIN_EXPONENT) / TILING_OCTAVES * BYTE_MAX).roundToInt().coerceIn(0, BYTE_MAX)
}

internal fun decodeTiling(encoded: Int): Float = 2f.pow(encoded / BYTE_MAX.toFloat() * TILING_OCTAVES + TILING_MIN_EXPONENT)
