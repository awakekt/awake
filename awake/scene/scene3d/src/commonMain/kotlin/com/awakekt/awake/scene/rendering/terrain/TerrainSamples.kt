/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.terrain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A terrain's heights as a read-only `List<Float>` over one `FloatArray`.
 *
 * [SceneTerrain.samples] is a `List<Float>` in released API, and a list of boxed floats held every
 * height as its own object: a region's 707,000 heights were 10.5 MiB of `Float`s and 2.7 MiB of the
 * array that held them, live for as long as the scene document. This holds the same values in 4 bytes
 * each. It reads like any list, compares equal to a list of the same floats, and boxes only a value
 * that is read out of it one at a time.
 */
internal class TerrainSampleList(private val values: FloatArray) :
    AbstractList<Float>(),
    RandomAccess {
    override val size: Int get() = values.size

    override fun get(index: Int): Float = values[index]

    /** The heights, copied, without boxing them. */
    fun toFloats(): FloatArray = values.copyOf()

    /** Whether every height is finite, read without boxing. */
    fun allFinite(): Boolean = values.all(Float::isFinite)
}

/** [samples] as a `FloatArray`, without boxing when it is a [TerrainSampleList]. */
internal fun terrainFloats(samples: List<Float>): FloatArray = (samples as? TerrainSampleList)?.toFloats() ?: samples.toFloatArray()

/** [samples] wrapped as a [SceneTerrain.samples] list, owning the array. */
internal fun terrainSamples(samples: FloatArray): List<Float> = TerrainSampleList(samples)

/**
 * [SceneTerrain.samples] read straight into a `FloatArray` and written from one.
 *
 * The descriptor is the one a `List<Float>` has, so a scene's JSON and its exported schema are the
 * same as before; only the decoded value differs, a [TerrainSampleList] instead of boxed floats.
 */
// decodeSequentially: the fast path a binary format takes; JSON reads element by element.
@OptIn(ExperimentalSerializationApi::class)
internal object TerrainSamplesSerializer : KSerializer<List<Float>> {
    override val descriptor: SerialDescriptor = ListSerializer(Float.serializer()).descriptor

    override fun serialize(encoder: Encoder, value: List<Float>) {
        val composite = encoder.beginCollection(descriptor, value.size)
        val floats = terrainFloats(value)
        for (index in floats.indices) composite.encodeFloatElement(descriptor, index, floats[index])
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): List<Float> {
        val composite = decoder.beginStructure(descriptor)
        val known = composite.decodeCollectionSize(descriptor)
        var values = FloatArray(if (known >= 0) known else INITIAL_CAPACITY)
        var size = 0
        if (composite.decodeSequentially() && known >= 0) {
            while (size < known) {
                values[size] = composite.decodeFloatElement(descriptor, size)
                size++
            }
        } else {
            while (true) {
                val index = composite.decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                if (size == values.size) values = values.copyOf(values.size * 2)
                values[size++] = composite.decodeFloatElement(descriptor, index)
            }
        }
        composite.endStructure(descriptor)
        return TerrainSampleList(if (size == values.size) values else values.copyOf(size))
    }

    private const val INITIAL_CAPACITY = 1024
}
