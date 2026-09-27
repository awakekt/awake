/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.render.texture.TextureAsset
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
    val layerSize = width * height * RGBA
    val data = ByteArray(layerSize * albedo.size)
    albedo.forEachIndexed { layer, image ->
        require(image.width == width && image.height == height && image.layerCount == 1) {
            "Layer $layer is ${image.width} x ${image.height} x ${image.layerCount}; every layer must be " +
                "a single $width x $height image. Resize them when importing."
        }
        image.data.copyInto(data, layer * layerSize, 0, layerSize)
        val heightImage = heights[layer]
        heightImage?.let {
            require(it.width == width && it.height == height) {
                "Layer $layer height is ${it.width} x ${it.height}; its albedo is $width x $height."
            }
        }
        for (texel in 0 until width * height) {
            data[layer * layerSize + texel * RGBA + ALPHA] = heightImage?.data?.get(texel * RGBA) ?: BYTE_MAX.toByte()
        }
    }
    if (albedo.size == 1) return TextureAsset(data + data, width, height, layerCount = 2)
    return TextureAsset(data, width, height, layerCount = albedo.size)
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
    return TextureAsset(data, palette.layers.size, 1)
}

/** The byte the shader turns back into a tiling with `2^(byte / 255 * 8 - 2)`. */
internal fun encodeTiling(tiling: Float): Int {
    val clamped = tiling.coerceIn(MIN_LAYER_TILING, MAX_LAYER_TILING)
    return ((log2(clamped) - TILING_MIN_EXPONENT) / TILING_OCTAVES * BYTE_MAX).roundToInt().coerceIn(0, BYTE_MAX)
}

internal fun decodeTiling(encoded: Int): Float = 2f.pow(encoded / BYTE_MAX.toFloat() * TILING_OCTAVES + TILING_MIN_EXPONENT)
