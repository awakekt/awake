/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.ui.DynamicMesh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Capacity belongs to one frame slot, not to the mesh.
 *
 * `DynamicMesh` allocates a buffer per frame-in-flight slot but once tracked capacity in a single
 * field. The first slot to outgrow its buffer reallocated and set that shared field, and every
 * other slot then believed it had room it had never been given -- so a frame landing on one of
 * those wrote past a buffer that was still at the initial size. Frames alternating between a grown
 * slot and a short one is what a flicker looks like from the outside.
 *
 * A single-frame render cannot see this by construction, which is why every existing headless test
 * missed it: the bug needs a second slot to be reached after the first has grown.
 *
 * [DynamicMesh.growthCount] is the decisive signal. With per-slot capacity, feeding an oversized
 * run to N slots grows N times. With one shared field it grows exactly once, and the remaining
 * N-1 slots are silently undersized.
 */
class DynamicMeshSlotGrowthTest {

    @Test
    fun everySlotGrowsForItselfRatherThanTrustingAnotherSlotsCapacity() {
        val device = GraphicsDevice()
        device.createHeadless()
        try {
            val mesh = DynamicMesh(
                graphicsDevice = device,
                maxQuads = INITIAL_QUADS,
                framesInFlight = FRAMES_IN_FLIGHT,
            )
            try {
                val vertices = quadVertices(OVERSIZED_QUADS)
                val indices = quadIndices(OVERSIZED_QUADS)

                repeat(FRAMES_IN_FLIGHT) { slot ->
                    mesh.update(frameIndex = slot, vertices = vertices, indices = indices)
                    assertEquals(
                        slot + 1,
                        mesh.growthCount,
                        "slot $slot did not grow: it inherited a capacity another slot's buffer has",
                    )
                }

                // Steady state converges. A count that keeps climbing on unchanged content would
                // mean the growth never took, which is the other way this can fail.
                mesh.update(frameIndex = 0, vertices = vertices, indices = indices)
                assertEquals(
                    FRAMES_IN_FLIGHT,
                    mesh.growthCount,
                    "a slot already large enough must not reallocate again",
                )
            } finally {
                mesh.destroy()
            }
        } finally {
            device.destroy()
        }
    }

    @Test
    fun aRunThatFitsNeverGrowsAnySlot() {
        val device = GraphicsDevice()
        device.createHeadless()
        try {
            val mesh = DynamicMesh(
                graphicsDevice = device,
                maxQuads = INITIAL_QUADS,
                framesInFlight = FRAMES_IN_FLIGHT,
            )
            try {
                val vertices = quadVertices(INITIAL_QUADS)
                val indices = quadIndices(INITIAL_QUADS)
                repeat(FRAMES_IN_FLIGHT) { mesh.update(frameIndex = it, vertices = vertices, indices = indices) }

                assertEquals(0, mesh.growthCount, "content within the initial capacity must not reallocate")
                assertTrue(mesh.drawIndexCount > 0, "the run still has to be recorded")
            } finally {
                mesh.destroy()
            }
        } finally {
            device.destroy()
        }
    }

    private fun quadVertices(quads: Int) = FloatArray(quads * VERTICES_PER_QUAD * FLOATS_PER_VERTEX)

    /** Index values are irrelevant here; only the count drives capacity. */
    private fun quadIndices(quads: Int) = IntArray(quads * INDICES_PER_QUAD)

    private companion object {
        const val INITIAL_QUADS = 4

        /** Comfortably past [INITIAL_QUADS], so every slot must reallocate to hold it. */
        const val OVERSIZED_QUADS = 64

        /** More than one, or the bug cannot appear at all. */
        const val FRAMES_IN_FLIGHT = 3

        const val VERTICES_PER_QUAD = 4
        const val INDICES_PER_QUAD = 6
        const val FLOATS_PER_VERTEX = 6
    }
}
