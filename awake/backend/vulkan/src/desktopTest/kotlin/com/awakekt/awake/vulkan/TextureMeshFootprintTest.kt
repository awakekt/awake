/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.renderer.GpuBufferPoolManager
import com.awakekt.awake.vulkan.ui.DynamicMesh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A screen of images reserves host-visible memory for what it draws, not a batch per image.
 *
 * Every textured primitive owns its mesh, one per frame slot. When each started at the 256-quad
 * batch size, a login screen of 190 single-quad images held 21 MB of host-visible memory for
 * 41 KB of geometry per frame. This was found while investigating Android 16 buffer-write crashes
 * (#1118); the capacity assertion guards over-allocation, not the emulator driver's mapping
 * behavior. At the old starting size, these 190 primitives request 21,012,480 capacity bytes:
 * 55,296 per primitive and slot, excluding the device allocator's padding.
 */
class TextureMeshFootprintTest {

    @Test
    fun aScreenOfSingleQuadImagesReservesWhatItDraws() {
        withPool { pool ->
            val quad = vertices(1) to indices(1)
            val meshes = (0 until PRIMITIVES).map { pool.textureMeshForPrimitive(it) }
            meshes.forEach { mesh -> repeat(FRAMES_IN_FLIGHT) { mesh.update(it, quad.first, quad.second) } }

            val reserved = meshes.sumOf { it.reservedBytes }
            assertTrue(reserved <= RESERVED_BUDGET_BYTES, "$PRIMITIVES single-quad images reserve $reserved bytes")
            assertEquals(0, meshes.sumOf { it.growthCount }, "a single quad fits the starting size")
        }
    }

    @Test
    fun aNineSliceImageGrowsItsOwnMeshToFit() {
        withPool { pool ->
            val mesh = pool.textureMeshForPrimitive(0)
            repeat(FRAMES_IN_FLIGHT) { mesh.update(it, vertices(NINE_SLICE_QUADS), indices(NINE_SLICE_QUADS)) }

            assertEquals(FRAMES_IN_FLIGHT, mesh.growthCount, "each slot grows once for a nine-slice")
            assertEquals(NINE_SLICE_QUADS * INDICES_PER_QUAD, mesh.drawIndexCount)
        }
    }

    private fun withPool(block: (GpuBufferPoolManager) -> Unit) {
        val device = GraphicsDevice()
        device.createHeadless()
        try {
            val pool = GpuBufferPoolManager(device, FRAMES_IN_FLIGHT)
            try {
                block(pool)
            } finally {
                pool.destroy()
            }
        } finally {
            device.destroy()
        }
    }

    private fun vertices(quads: Int) = FloatArray(quads * VERTICES_PER_QUAD * DynamicMesh.GLYPH_FLOATS_PER_VERTEX)

    /** Only the count drives capacity. */
    private fun indices(quads: Int) = IntArray(quads * INDICES_PER_QUAD)

    private companion object {
        /** The login screen that crashed drew 188 textured primitives. */
        const val PRIMITIVES = 190
        const val FRAMES_IN_FLIGHT = 2
        const val NINE_SLICE_QUADS = 9
        const val VERTICES_PER_QUAD = 4
        const val INDICES_PER_QUAD = 6

        /** Room for a few quads per primitive and slot: an order of magnitude under the old 21 MB. */
        const val RESERVED_BUDGET_BYTES = 1L shl 20
    }
}
