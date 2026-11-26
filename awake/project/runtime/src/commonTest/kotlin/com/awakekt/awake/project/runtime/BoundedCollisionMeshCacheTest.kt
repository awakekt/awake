/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.host.DeviceProfile
import com.awakekt.awake.physics.MeshShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Verifies LRU eviction, recency refresh, `trimMemory()`, and device-scaled caps on [BoundedCollisionMeshCache]. */
class BoundedCollisionMeshCacheTest {

    @Test
    fun evictsLeastRecentlyUsedEntriesAndClearsOnTrimMemory() {
        val cache = BoundedCollisionMeshCache(maxEntries = 2)
        val a = shape(0f)
        val b = shape(1f)
        val c = shape(2f)

        cache.put("a.glb", null, a)
        cache.put("b.glb", null, b)
        assertEquals(2, cache.size)

        // Touch 'a' so 'b' becomes least-recently-used.
        assertSame(a, cache.meshShape("a.glb", null))
        cache.put("c.glb", null, c)

        assertEquals(2, cache.size)
        assertSame(a, cache.meshShape("a.glb", null))
        assertNull(cache.meshShape("b.glb", null), "LRU entry must be evicted when over capacity")
        assertSame(c, cache.meshShape("c.glb", null))

        cache.trimMemory()
        assertEquals(0, cache.size)
        assertNull(cache.meshShape("a.glb", null))
    }

    @Test
    fun defaultCapScalesWithDeviceProfile() {
        assertEquals(
            BoundedCollisionMeshCache.LOW_RAM_MAX_ENTRIES,
            BoundedCollisionMeshCache.defaultMaxEntries(DeviceProfile(totalMemoryBytes = 4L * 1024 * 1024 * 1024)),
        )
        assertEquals(
            BoundedCollisionMeshCache.LOW_RAM_MAX_ENTRIES,
            BoundedCollisionMeshCache.defaultMaxEntries(DeviceProfile(isLowRamDevice = true)),
        )
        assertEquals(
            BoundedCollisionMeshCache.DEFAULT_MAX_ENTRIES,
            BoundedCollisionMeshCache.defaultMaxEntries(DeviceProfile(totalMemoryBytes = 12L * 1024 * 1024 * 1024)),
        )
    }

    private fun shape(offset: Float): MeshShape = MeshShape(
        vertices = floatArrayOf(offset, 0f, 0f, offset + 1f, 0f, 0f, offset, 1f, 0f),
        indices = intArrayOf(0, 1, 2),
    )
}
