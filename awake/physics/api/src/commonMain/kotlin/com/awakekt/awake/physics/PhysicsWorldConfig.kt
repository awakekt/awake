/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

/**
 * Capacity, threading, temporary-allocator, and shape-cache budgets for constructing a
 * [PhysicsWorld].
 *
 * Backends default to [Desktop] on desktop targets and [Mobile] on Android/mobile targets so
 * 4 GB RAM devices avoid pre-allocating 65K-body broadphase tables, spawning 8 physics worker
 * threads, or pinning unbounded native `MeshShape` BVHs across zone transitions.
 *
 * @property maxBodies Maximum simultaneous rigid bodies in the simulation.
 * @property maxBodyPairs Maximum overlapping broadphase body pairs buffered per step.
 * @property maxContactConstraints Maximum simultaneous contact constraints solved per step.
 * @property workerThreads Worker thread count for parallel simulation; `0` selects the backend's
 *   platform default (1-2 workers on mobile, available cores on desktop).
 * @property tempAllocatorBytes Fixed temporary step-allocator size in bytes; `0` selects the
 *   backend's platform default (`TempAllocatorMalloc` on desktop, 2 MiB fixed allocator on mobile).
 * @property maxCachedMeshShapes Maximum unreferenced `MeshShape` BVHs kept warm in LRU cache after
 *   their last body is destroyed (`0` evicts immediately on last body destruction).
 */
data class PhysicsWorldConfig(
    val maxBodies: Int = DESKTOP_MAX_BODIES,
    val maxBodyPairs: Int = DESKTOP_MAX_BODY_PAIRS,
    val maxContactConstraints: Int = DESKTOP_MAX_CONTACT_CONSTRAINTS,
    val workerThreads: Int = AUTO_WORKER_THREADS,
    val tempAllocatorBytes: Int = AUTO_TEMP_ALLOCATOR_BYTES,
    val maxCachedMeshShapes: Int = DESKTOP_MAX_CACHED_MESH_SHAPES,
) {
    init {
        require(maxBodies > 0) { "maxBodies must be positive, was $maxBodies" }
        require(maxBodyPairs > 0) { "maxBodyPairs must be positive, was $maxBodyPairs" }
        require(maxContactConstraints > 0) { "maxContactConstraints must be positive, was $maxContactConstraints" }
        require(workerThreads >= 0) { "workerThreads must be >= 0 (0 = auto), was $workerThreads" }
        require(tempAllocatorBytes >= 0) { "tempAllocatorBytes must be >= 0 (0 = backend default), was $tempAllocatorBytes" }
        require(maxCachedMeshShapes >= 0) { "maxCachedMeshShapes must be >= 0, was $maxCachedMeshShapes" }
    }

    /** Standard presets and limit constants for [PhysicsWorldConfig]. */
    companion object {
        /** Sentinel instructing the backend to pick its platform-appropriate worker count. */
        const val AUTO_WORKER_THREADS: Int = 0

        /** Sentinel instructing the backend to pick its platform-appropriate temp allocator. */
        const val AUTO_TEMP_ALLOCATOR_BYTES: Int = 0

        /** Default desktop body ceiling. */
        const val DESKTOP_MAX_BODIES: Int = 65_536

        /** Default desktop broadphase body-pair ceiling. */
        const val DESKTOP_MAX_BODY_PAIRS: Int = 65_536

        /** Default desktop contact-constraint ceiling. */
        const val DESKTOP_MAX_CONTACT_CONSTRAINTS: Int = 20_480

        /** Default desktop warm `MeshShape` BVH cache ceiling. */
        const val DESKTOP_MAX_CACHED_MESH_SHAPES: Int = 256

        /** Default mobile body ceiling (8x smaller broadphase tables than desktop). */
        const val MOBILE_MAX_BODIES: Int = 8_192

        /** Default mobile broadphase body-pair ceiling. */
        const val MOBILE_MAX_BODY_PAIRS: Int = 8_192

        /** Default mobile contact-constraint ceiling. */
        const val MOBILE_MAX_CONTACT_CONSTRAINTS: Int = 2_048

        /** Default mobile fixed temporary step allocator size (2 MiB, avoids per-step malloc churn). */
        const val MOBILE_TEMP_ALLOCATOR_BYTES: Int = 2 * 1024 * 1024

        /** Default mobile warm `MeshShape` BVH cache ceiling. */
        const val MOBILE_MAX_CACHED_MESH_SHAPES: Int = 64

        /** Default desktop physics budgets. */
        val Desktop: PhysicsWorldConfig = PhysicsWorldConfig()

        /** Default mobile physics budgets targeting 4 GB RAM devices. */
        val Mobile: PhysicsWorldConfig = PhysicsWorldConfig(
            maxBodies = MOBILE_MAX_BODIES,
            maxBodyPairs = MOBILE_MAX_BODY_PAIRS,
            maxContactConstraints = MOBILE_MAX_CONTACT_CONSTRAINTS,
            workerThreads = AUTO_WORKER_THREADS,
            tempAllocatorBytes = MOBILE_TEMP_ALLOCATOR_BYTES,
            maxCachedMeshShapes = MOBILE_MAX_CACHED_MESH_SHAPES,
        )
    }
}
