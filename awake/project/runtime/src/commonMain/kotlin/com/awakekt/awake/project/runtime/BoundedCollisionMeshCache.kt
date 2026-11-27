/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.host.DeviceProfile
import com.awakekt.awake.core.host.readDeviceProfile
import com.awakekt.awake.core.pool.BoundedLruCache
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.scene.physics.CollisionMeshSource

/**
 * Bounded LRU [CollisionMeshSource] for streaming worlds that extract collision triangles on
 * demand and must cap retained CPU [MeshShape] vertex/index arrays on 4 GB RAM devices.
 *
 * @param maxEntries Maximum number of `(meshPath, primitive)` shapes retained before oldest entries
 *   are evicted; defaults to [defaultMaxEntries].
 */
class BoundedCollisionMeshCache(
    maxEntries: Int = defaultMaxEntries(),
) : CollisionMeshSource {
    /** Current maximum number of cached collision shapes; lowering evicts oldest entries immediately. */
    var maxEntries: Int = maxEntries.coerceAtLeast(1)
        set(value) {
            field = value.coerceAtLeast(1)
            cache.evictOverLimit()
        }

    private val cache = BoundedLruCache<Pair<String, Int?>, MeshShape> { this.maxEntries }

    /** Number of collision shapes currently retained in this cache. */
    val size: Int get() = cache.size

    override fun meshShape(mesh: String, primitive: Int?): MeshShape? = cache.get(mesh to primitive)

    /** Stores [shape] under `(mesh, primitive)` and evicts the least-recently-used entry if over cap. */
    fun put(mesh: String, primitive: Int?, shape: MeshShape) {
        cache.put(mesh to primitive, shape)
    }

    /** Removes `(mesh, primitive)` from the cache if present. */
    fun remove(mesh: String, primitive: Int?): MeshShape? = cache.remove(mesh to primitive)

    /** Releases all retained CPU collision shapes under memory pressure. */
    fun trimMemory() {
        cache.clear()
    }

    /** Device-scaled LRU defaults for [BoundedCollisionMeshCache]. */
    companion object {
        private const val LOW_RAM_BYTES: Long = 4L * 1024L * 1024L * 1024L

        /** Default LRU ceiling on low-RAM / 4 GB devices. */
        const val LOW_RAM_MAX_ENTRIES: Int = 64

        /** Default LRU ceiling on devices with more than 4 GB RAM. */
        const val DEFAULT_MAX_ENTRIES: Int = 256

        /** Returns the recommended collision-mesh cache ceiling for [profile]. */
        fun defaultMaxEntries(profile: DeviceProfile = readDeviceProfile()): Int {
            val total = profile.totalMemoryBytes
            val isLowRam = profile.isLowRamDevice || (total != null && total in 1L..LOW_RAM_BYTES)
            return if (isLowRam) LOW_RAM_MAX_ENTRIES else DEFAULT_MAX_ENTRIES
        }
    }
}
