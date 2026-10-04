/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.render.texture.TextureAsset

/** Layer slots a control texel holds where at most this many layers meet: one RGBA texel each. */
const val CONTROL_SLOTS: Int = 4

/** Layer slots a control texel holds where more than [CONTROL_SLOTS] layers meet. */
const val MAX_CONTROL_SLOTS: Int = 8

/** The control texels one pixel blends: its 2 x 2 neighbourhood. */
private const val PIXEL_TEXELS = 4

private const val BYTE_MAX = 255

/**
 * Which palette layers cover each terrain texel, and how strongly: the [slots] strongest, with
 * weights summing to 255. Unused slots have weight 0. Texels are row-major, `z * width + x`, the
 * same layout as a heightmap.
 *
 * [slots] is [CONTROL_SLOTS] or [MAX_CONTROL_SLOTS]. The shader's per-pixel cost follows it.
 */
class TerrainControlMap(
    val width: Int,
    val depth: Int,
    indices: ByteArray,
    weights: ByteArray,
    val slots: Int = CONTROL_SLOTS,
) {
    private val indexBytes = indices.copyOf()
    private val weightBytes = weights.copyOf()

    init {
        require(width >= 1 && depth >= 1) { "A control map needs at least 1 x 1 texels; was $width x $depth." }
        require(slots == CONTROL_SLOTS || slots == MAX_CONTROL_SLOTS) {
            "A control texel holds $CONTROL_SLOTS or $MAX_CONTROL_SLOTS layers; was $slots."
        }
        val expected = width.toLong() * depth * slots
        require(indices.size.toLong() == expected && weights.size.toLong() == expected) {
            "A $width x $depth control map needs $expected index and weight bytes; " +
                "got ${indices.size} and ${weights.size}."
        }
    }

    fun layerAt(x: Int, z: Int, slot: Int): Int = indexBytes[offset(x, z, slot)].toInt() and BYTE_MAX

    fun weightAt(x: Int, z: Int, slot: Int): Int = weightBytes[offset(x, z, slot)].toInt() and BYTE_MAX

    /** The highest palette index any slot with weight uses, or -1 for an empty map. */
    fun highestLayer(): Int {
        var highest = -1
        for (i in indexBytes.indices) if (weightBytes[i] != 0.toByte()) highest = maxOf(highest, indexBytes[i].toInt() and BYTE_MAX)
        return highest
    }

    fun copyIndices(): ByteArray = indexBytes.copyOf()

    fun copyWeights(): ByteArray = weightBytes.copyOf()

    /**
     * Indices in RGBA order for the shader's control binding: `slots / 4` texels side by side per
     * control texel.
     */
    fun indicesTexture(): TextureAsset = TextureAsset(indexBytes.copyOf(), width * slots / CONTROL_SLOTS, depth)

    fun weightsTexture(): TextureAsset = TextureAsset(weightBytes.copyOf(), width * slots / CONTROL_SLOTS, depth)

    private fun offset(x: Int, z: Int, slot: Int): Int {
        require(x in 0 until width && z in 0 until depth && slot in 0 until slots) {
            "Control texel ($x, $z) slot $slot is outside $width x $depth x $slots."
        }
        return (z * width + x) * slots + slot
    }

    companion object {
        /**
         * Keeps each texel's strongest layers and rescales them to sum to 255, reporting how much
         * weight the cut removed. A texel with no weight at all shows layer 0.
         *
         * The map holds [CONTROL_SLOTS] layers per texel, or [MAX_CONTROL_SLOTS] where one texel,
         * or the 2 x 2 texels one pixel blends, hold more than [CONTROL_SLOTS] between them.
         *
         * @param width Texels along x.
         * @param depth Texels along z.
         * @param layerCount Palette size; `weight` is asked about layers `0 until layerCount`.
         * @param weight Non-negative coverage of `layer` at texel (`x`, `z`), in any common unit.
         */
        fun reduce(width: Int, depth: Int, layerCount: Int, weight: (layer: Int, x: Int, z: Int) -> Float): TerrainControlReduction {
            require(layerCount in 1..MAX_TERRAIN_LAYERS) { "layerCount $layerCount is outside 1..$MAX_TERRAIN_LAYERS." }
            val indices = ByteArray(width * depth * MAX_CONTROL_SLOTS)
            val weights = ByteArray(width * depth * MAX_CONTROL_SLOTS)
            val texel = TexelReduction(layerCount, weight)
            var maxDropped = 0f
            var droppedSum = 0f
            var texelsOverSlots = 0
            var mostCovering = 0
            for (z in 0 until depth) {
                for (x in 0 until width) {
                    texel.reduce(x, z)
                    texel.write((z * width + x) * MAX_CONTROL_SLOTS, indices, weights)
                    maxDropped = maxOf(maxDropped, texel.dropped)
                    droppedSum += texel.dropped
                    if (texel.covering > MAX_CONTROL_SLOTS) texelsOverSlots++
                    mostCovering = maxOf(mostCovering, texel.covering)
                }
            }
            val wide = mostCovering > CONTROL_SLOTS || mostLayersAPixelBlends(width, depth, indices, weights) > CONTROL_SLOTS
            return TerrainControlReduction(
                controlMap = if (wide) {
                    TerrainControlMap(width, depth, indices, weights, MAX_CONTROL_SLOTS)
                } else {
                    TerrainControlMap(width, depth, indices.firstSlots(), weights.firstSlots())
                },
                maxDroppedWeight = maxDropped,
                meanDroppedWeight = droppedSum / (width * depth),
                texelsOverSlots = texelsOverSlots,
            )
        }
    }
}

/** The most layers with weight among any 2 x 2 texels of an [MAX_CONTROL_SLOTS] map. */
private fun mostLayersAPixelBlends(width: Int, depth: Int, indices: ByteArray, weights: ByteArray): Int {
    val seen = IntArray(PIXEL_TEXELS * MAX_CONTROL_SLOTS)
    var most = 0
    for (texel in 0 until width * depth) {
        seen.fill(-1)
        var count = 0
        for (i in seen.indices) {
            val tap = i / MAX_CONTROL_SLOTS
            val x = minOf(texel % width + tap % 2, width - 1)
            val z = minOf(texel / width + tap / 2, depth - 1)
            val slot = (z * width + x) * MAX_CONTROL_SLOTS + i % MAX_CONTROL_SLOTS
            val layer = indices[slot].toInt() and BYTE_MAX
            if (weights[slot] != 0.toByte() && layer !in seen) seen[count++] = layer
        }
        most = maxOf(most, count)
    }
    return most
}

/** Each texel's first [CONTROL_SLOTS] of [MAX_CONTROL_SLOTS]. */
private fun ByteArray.firstSlots(): ByteArray =
    ByteArray(size / MAX_CONTROL_SLOTS * CONTROL_SLOTS) { this[it / CONTROL_SLOTS * MAX_CONTROL_SLOTS + it % CONTROL_SLOTS] }

/** One texel's reduction, reused across texels so the loop allocates nothing per texel. */
private class TexelReduction(private val layerCount: Int, private val weight: (layer: Int, x: Int, z: Int) -> Float) {
    private val strongest = IntArray(MAX_CONTROL_SLOTS)
    private val strongestWeight = FloatArray(MAX_CONTROL_SLOTS)
    private var total = 0f

    /** Layers with any weight at the last reduced texel. */
    var covering = 0
        private set

    /** Fraction of the last texel's weight that did not fit in [MAX_CONTROL_SLOTS]. */
    var dropped = 0f
        private set

    fun reduce(x: Int, z: Int) {
        strongest.fill(0)
        strongestWeight.fill(0f)
        total = 0f
        covering = 0
        for (layer in 0 until layerCount) {
            val w = weight(layer, x, z)
            require(w.isFinite() && w >= 0f) { "Weight of layer $layer at ($x, $z) is $w; weights are finite and >= 0." }
            if (w > 0f) {
                total += w
                covering++
                insert(layer, w)
            }
        }
        dropped = if (total == 0f) 0f else (total - strongestWeight.sum()) / total
    }

    /** Rounds the kept weights to bytes summing to exactly 255, leftovers to the strongest. */
    fun write(base: Int, indices: ByteArray, weights: ByteArray) {
        if (total == 0f) {
            weights[base] = BYTE_MAX.toByte()
            return
        }
        val kept = strongestWeight.sum()
        var assigned = 0
        for (slot in 0 until MAX_CONTROL_SLOTS) {
            val share = (strongestWeight[slot] / kept * BYTE_MAX).toInt()
            indices[base + slot] = strongest[slot].toByte()
            weights[base + slot] = share.toByte()
            assigned += share
        }
        weights[base] = ((weights[base].toInt() and BYTE_MAX) + BYTE_MAX - assigned).toByte()
    }

    /** Keeps [strongest] sorted strongest-first; a candidate weaker than every slot is ignored. */
    private fun insert(layer: Int, w: Float) {
        var slot = MAX_CONTROL_SLOTS
        while (slot > 0 && strongestWeight[slot - 1] < w) slot--
        if (slot == MAX_CONTROL_SLOTS) return
        for (i in MAX_CONTROL_SLOTS - 1 downTo slot + 1) {
            strongest[i] = strongest[i - 1]
            strongestWeight[i] = strongestWeight[i - 1]
        }
        strongest[slot] = layer
        strongestWeight[slot] = w
    }
}

/**
 * A control map and what reducing it to at most [MAX_CONTROL_SLOTS] layers per texel cost, as a
 * fraction of each texel's total weight.
 */
class TerrainControlReduction(
    val controlMap: TerrainControlMap,
    val maxDroppedWeight: Float,
    val meanDroppedWeight: Float,
    /** Texels covered by more layers than a control texel holds. */
    val texelsOverSlots: Int,
)

/**
 * `*.terrainctl`: a small header, then every texel's indices, then every texel's weights.
 *
 * Binary rather than PNG because this is data, not an image: platform image decoders may flip
 * rows, premultiply alpha, or colour-manage, and any of those corrupts an index.
 *
 * Layout, little-endian: `ATCM`, version byte, slots per texel, two reserved bytes, width u32,
 * depth u32, `width * depth * slots` index bytes, `width * depth * slots` weight bytes. Version 1
 * has no slots byte and holds [CONTROL_SLOTS].
 */
object TerrainControlMapCodec {
    private val MAGIC = "ATCM".encodeToByteArray()
    private const val VERSION: Byte = 2
    private const val HEADER = 16

    fun encode(map: TerrainControlMap): ByteArray {
        val planeSize = map.width * map.depth * map.slots
        val out = ByteArray(HEADER + planeSize * 2)
        MAGIC.copyInto(out)
        out[4] = VERSION
        out[5] = map.slots.toByte()
        writeInt(out, 8, map.width)
        writeInt(out, 12, map.depth)
        map.copyIndices().copyInto(out, HEADER)
        map.copyWeights().copyInto(out, HEADER + planeSize)
        return out
    }

    fun decode(bytes: ByteArray): TerrainControlMap {
        require(bytes.size >= HEADER && bytes.copyOfRange(0, 4).contentEquals(MAGIC)) { "Not a terrain control map: missing ATCM header." }
        require(bytes[4] in 1..VERSION) { "Terrain control map version ${bytes[4]} is not supported; expected 1 to $VERSION." }
        val slots = if (bytes[4] == 1.toByte()) CONTROL_SLOTS else bytes[5].toInt()
        val width = readInt(bytes, 8)
        val depth = readInt(bytes, 12)
        require(width in 1..MAX_SIDE && depth in 1..MAX_SIDE) { "Terrain control map size $width x $depth is outside 1..$MAX_SIDE." }
        require(slots == CONTROL_SLOTS || slots == MAX_CONTROL_SLOTS) { "Terrain control map texels hold $slots layers; expected $CONTROL_SLOTS or $MAX_CONTROL_SLOTS." }
        val planeSize = width * depth * slots
        require(bytes.size == HEADER + planeSize * 2) {
            "A $width x $depth terrain control map is ${HEADER + planeSize * 2} bytes; got ${bytes.size}."
        }
        return TerrainControlMap(
            width,
            depth,
            bytes.copyOfRange(HEADER, HEADER + planeSize),
            bytes.copyOfRange(HEADER + planeSize, HEADER + planeSize * 2),
            slots,
        )
    }

}

/** The largest 2D texture every WebGPU device must accept. */
internal const val MAX_SIDE = 8192

internal fun writeInt(out: ByteArray, at: Int, value: Int) {
    for (i in 0 until Int.SIZE_BYTES) out[at + i] = (value ushr (i * Byte.SIZE_BITS)).toByte()
}

internal fun readInt(bytes: ByteArray, at: Int): Int {
    var value = 0
    for (i in 0 until Int.SIZE_BYTES) value = value or ((bytes[at + i].toInt() and BYTE_MAX) shl (i * Byte.SIZE_BITS))
    return value
}
