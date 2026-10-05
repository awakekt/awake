/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Layers one terrain can use: the control map stores a palette index in one byte. */
const val MAX_TERRAIN_LAYERS: Int = 256

/** Shortest and longest texture repeat, in world units, the layer table can encode. */
const val MIN_LAYER_TILING: Float = 0.25f

/** Maximum allowed tiling factor for a terrain layer. */
const val MAX_LAYER_TILING: Float = 64f

/**
 * One surface a terrain can show.
 *
 * @property id Stable name; tools reference layers by it, the control map by palette position.
 * @property albedo Colour image, repeated across the terrain. Its alpha is ignored.
 * @property height Optional greyscale image driving height blending; without one the layer is flat.
 * @property tiling World units one repeat of [albedo] covers, within [MIN_LAYER_TILING]..[MAX_LAYER_TILING].
 * @property blendSharpness 0 blends linearly by weight, as most legacy terrain formats do; 1 lets
 * the surface standing higher by weight plus [height] cover its neighbour where they meet.
 * @property physicalMaterial Opaque id for gameplay (footsteps, friction); the renderer ignores it.
 */
@Serializable
data class TerrainLayer(
    val id: String,
    val albedo: String,
    val height: String? = null,
    val tiling: Float = 4f,
    val blendSharpness: Float = 0.5f,
    val physicalMaterial: String? = null,
)

/** The ordered layers of one terrain. A layer's position is the index the control map stores. */
@Serializable
data class TerrainLayerPalette(
    /** Serialized schema format version. */
    val formatVersion: Int = FORMAT_VERSION,
    /** List of terrain surface layers in palette index order. */
    val layers: List<TerrainLayer>,
) {
    /** Everything wrong with this palette; empty when it can be drawn. */
    fun validate(): List<String> = buildList {
        if (formatVersion != FORMAT_VERSION) add("formatVersion $formatVersion is not supported; expected $FORMAT_VERSION")
        if (layers.isEmpty()) add("a palette needs at least one layer")
        if (layers.size > MAX_TERRAIN_LAYERS) add("${layers.size} layers exceed the $MAX_TERRAIN_LAYERS a control map can index")
        layers.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { add("layer id '$it' is used more than once") }
        layers.forEach { layer ->
            if (layer.albedo.isBlank()) add("layer '${layer.id}' has no albedo image")
            if (!layer.tiling.isFinite() || layer.tiling !in MIN_LAYER_TILING..MAX_LAYER_TILING) {
                add("layer '${layer.id}' tiling ${layer.tiling} is outside $MIN_LAYER_TILING..$MAX_LAYER_TILING")
            }
            if (!layer.blendSharpness.isFinite() || layer.blendSharpness !in 0f..1f) {
                add("layer '${layer.id}' blendSharpness ${layer.blendSharpness} is outside 0..1")
            }
        }
    }

    /** Companion object containing format constants. */
    companion object {
        /** Current format version integer for terrain layer palettes. */
        const val FORMAT_VERSION = 1
    }
}

/** `*.terrainpalette.json`. Pure: bytes in, palette out, nothing read from anywhere. */
object TerrainLayerPaletteCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** Deserializes a UTF-8 JSON byte array into a validated [TerrainLayerPalette]. */
    fun decode(bytes: ByteArray): TerrainLayerPalette {
        val palette = json.decodeFromString(TerrainLayerPalette.serializer(), bytes.decodeToString())
        val issues = palette.validate()
        require(issues.isEmpty()) { "Invalid terrain layer palette: ${issues.joinToString("; ")}" }
        return palette
    }

    /** Serializes [palette] into a pretty-printed UTF-8 JSON byte array. */
    fun encode(palette: TerrainLayerPalette): ByteArray =
        json.encodeToString(TerrainLayerPalette.serializer(), palette).encodeToByteArray()
}
