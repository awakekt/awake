/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/** Verifies content-based [MeshShape] equality/hashing and [PhysicsWorldConfig] presets. */
class MeshShapeEqualityAndConfigTest {

    @Test
    fun meshShapeUsesContentBasedEqualsAndHashCode() {
        val a = MeshShape(
            vertices = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
            indices = intArrayOf(0, 1, 2),
        )
        val b = MeshShape(
            vertices = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
            indices = intArrayOf(0, 1, 2),
        )
        val differentVerts = MeshShape(
            vertices = floatArrayOf(0f, 0f, 0f, 2f, 0f, 0f, 0f, 1f, 0f),
            indices = intArrayOf(0, 1, 2),
        )
        val differentIndices = MeshShape(
            vertices = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
            indices = intArrayOf(0, 2, 1),
        )

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, differentVerts)
        assertNotEquals(a, differentIndices)

        val map = hashMapOf(a to "cached")
        assertEquals("cached", map[b], "Distinct MeshShape with identical arrays must hit cache")
    }

    @Test
    fun mobilePresetUsesCompactBudgetsAndConfigValidatesBounds() {
        val mobile = PhysicsWorldConfig.Mobile
        assertEquals(PhysicsWorldConfig.MOBILE_MAX_BODIES, mobile.maxBodies)
        assertEquals(PhysicsWorldConfig.MOBILE_MAX_BODY_PAIRS, mobile.maxBodyPairs)
        assertEquals(PhysicsWorldConfig.MOBILE_MAX_CONTACT_CONSTRAINTS, mobile.maxContactConstraints)
        assertEquals(PhysicsWorldConfig.MOBILE_TEMP_ALLOCATOR_BYTES, mobile.tempAllocatorBytes)
        assertEquals(PhysicsWorldConfig.MOBILE_MAX_CACHED_MESH_SHAPES, mobile.maxCachedMeshShapes)

        assertFailsWith<IllegalArgumentException> { PhysicsWorldConfig(maxBodies = 0) }
        assertFailsWith<IllegalArgumentException> { PhysicsWorldConfig(workerThreads = -1) }
        assertFailsWith<IllegalArgumentException> { PhysicsWorldConfig(tempAllocatorBytes = -1) }
        assertFailsWith<IllegalArgumentException> { PhysicsWorldConfig(maxCachedMeshShapes = -1) }
    }
}
