/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.terrain

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A decoded terrain holds its heights in one `FloatArray`, not a boxed `Float` each: a region's
 * 707,000 heights were 13 MiB of live heap in a populated scene a consumer measured, for as long as
 * the scene document stayed loaded. The JSON a terrain reads and writes is unchanged.
 *
 * Desktop-only: it measures the JVM heap.
 */
class TerrainSamplesHeapTest {
    private val json = Json

    @Test
    fun aDecodedTerrainHoldsItsHeightsInOneFloatArray() {
        val text = terrainJson(SIDE)
        // One decode first, so the format's own buffers and caches are not counted as the terrains'.
        check(json.decodeFromString(SceneTerrain.serializer(), text).samples.size == SIDE * SIDE)
        // Several terrains small enough that no array is a G1 humongous object, which the heap counts
        // as whole regions and so would read as more than it holds.
        val before = liveHeap()
        val terrains = List(TERRAINS) { json.decodeFromString(SceneTerrain.serializer(), text) }
        val retained = liveHeap() - before

        val heights = SIDE * SIDE * TERRAINS
        println("$TERRAINS SceneTerrains of ${SIDE * SIDE} heights retain ${retained / KIB} KiB (${retained * 10 / heights / 10.0} B a height)")
        assertEquals(heights, terrains.sumOf { it.samples.size })
        // One float array is 4 bytes a height; a boxed Float each was 16 bytes plus a 4-byte reference.
        assertTrue(retained < heights * MAX_BYTES_PER_HEIGHT, "decoded terrains retained $retained B for $heights heights")
    }

    @Test
    fun aTerrainReadsAndWritesTheSameJsonAsAListOfFloats() {
        val text = terrainJson(8)
        val decoded = json.decodeFromString(SceneTerrain.serializer(), text)
        val boxed = decoded.copy(samples = decoded.samples.toList())

        assertEquals(boxed, decoded, "a decoded terrain equals one holding a plain list of the same heights")
        assertEquals(boxed.hashCode(), decoded.hashCode())
        assertEquals(json.parseToJsonElement(text), json.parseToJsonElement(json.encodeToString(SceneTerrain.serializer(), decoded)))
        assertEquals(
            json.encodeToString(SceneTerrain.serializer(), boxed),
            json.encodeToString(SceneTerrain.serializer(), decoded),
            "a plain list and the decoded one write the same JSON",
        )
        assertEquals(ListSerializer(Float.serializer()).descriptor, SceneTerrain.serializer().descriptor.getElementDescriptor(SAMPLES_INDEX))
    }

    @Test
    fun anExportedTerrainHoldsItsHeightsInOneFloatArray() {
        val decoded = json.decodeFromString(SceneTerrain.serializer(), terrainJson(4))
        val exported = terrainSamples(terrainFloats(decoded.samples))
        assertTrue(exported is TerrainSampleList)
        assertEquals(decoded.samples, exported)
    }

    private fun terrainJson(side: Int): String = buildJsonObject {
        put("width", side)
        put("depth", side)
        put("samples", JsonArray(List(side * side) { JsonPrimitive((it % 97) * 0.25f) }))
    }.toString()

    private fun liveHeap(): Long {
        val memory = ManagementFactory.getMemoryMXBean()
        repeat(GC_ROUNDS) {
            System.gc()
            Thread.sleep(GC_PAUSE_MS)
        }
        return memory.heapMemoryUsage.used
    }

    private companion object {
        const val SIDE = 256
        const val TERRAINS = 16
        const val MAX_BYTES_PER_HEIGHT = 8
        const val KIB = 1024
        const val GC_ROUNDS = 3
        const val GC_PAUSE_MS = 50L

        /** `samples` is SceneTerrain's sixth element: width, depth, scaleX, scaleY, scaleZ, samples. */
        const val SAMPLES_INDEX = 5
    }
}
