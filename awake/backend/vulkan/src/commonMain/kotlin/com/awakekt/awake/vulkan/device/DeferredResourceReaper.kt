/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

/**
 * Defers destruction of GPU resources (`Mesh`, `Material`, `VulkanDynamicMesh`) until every frame
 * in flight and pending offscreen submission that could still reference them has completed.
 *
 * Eliminates mid-frame `vkDeviceWaitIdle` pipeline stalls during streaming and scene replacement
 * while preserving leak-free teardown under Vulkan validation layers.
 */
internal class DeferredResourceReaper {
    private val lock = QueueLock()
    private var retiredAt = LongArray(INITIAL_CAPACITY)
    private var actions = arrayOfNulls<(() -> Unit)>(INITIAL_CAPACITY)
    private var count = 0

    /** Frames submitted so far; a retired resource waits until this passes its frame slots. */
    var framesSubmitted: Long = 0L
        private set

    /** Retired resources not yet destroyed, for tests. */
    val pending: Int get() = lock.withLock { count }

    /** Queues [cleanup] to execute once all currently in-flight frames have completed. */
    fun retire(cleanup: () -> Unit) {
        lock.withLock {
            if (count == actions.size) {
                val nextSize = count * 2
                retiredAt = retiredAt.copyOf(nextSize)
                actions = actions.copyOf(nextSize)
            }
            retiredAt[count] = framesSubmitted
            actions[count] = cleanup
            count++
        }
    }

    /**
     * Advances the submitted-frame counter and destroys every resource retired more than
     * [framesInFlight] frames ago, after [awaitOffscreen] ensures any earlier offscreen work has
     * finished. Steady-state frames with nothing retired cost one check and zero allocations.
     */
    fun frameSubmitted(framesInFlight: Int, awaitOffscreen: () -> Unit) {
        val hasReady = lock.withLock {
            framesSubmitted++
            count > 0 && framesSubmitted - retiredAt[0] > framesInFlight
        }
        if (!hasReady) return
        awaitOffscreen()
        val toRun = mutableListOf<() -> Unit>()
        lock.withLock {
            var kept = 0
            for (index in 0 until count) {
                val action = actions[index]
                if (framesSubmitted - retiredAt[index] > framesInFlight) {
                    if (action != null) toRun += action
                } else {
                    retiredAt[kept] = retiredAt[index]
                    actions[kept] = action
                    kept++
                }
            }
            for (index in kept until count) {
                actions[index] = null
            }
            count = kept
        }
        toRun.forEach { it.invoke() }
    }

    /** Executes every pending cleanup action immediately; caller has waited for device idle. */
    fun destroyAll() {
        val toRun = mutableListOf<() -> Unit>()
        lock.withLock {
            for (index in 0 until count) {
                actions[index]?.let { toRun += it }
                actions[index] = null
            }
            count = 0
        }
        toRun.forEach { it.invoke() }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
    }
}

/** Registers [reaper] so `Mesh`/`Material`/`VulkanDynamicMesh` retire without stalling the GPU. */
internal fun GraphicsDevice.registerReaper(reaper: DeferredResourceReaper) {
    if (activeReaper == null) activeReaper = reaper
}

/** Detaches [reaper] when its owning renderer tears down. */
internal fun GraphicsDevice.unregisterReaper(reaper: DeferredResourceReaper) {
    if (activeReaper === reaper) activeReaper = null
}

/**
 * Retires [cleanup] into [GraphicsDevice.activeReaper] when a presentable renderer is driving
 * frames, avoiding a full-GPU `vkDeviceWaitIdle` stall; falls back to `vkDeviceWaitIdle` +
 * immediate cleanup when no frame loop is attached.
 */
internal fun GraphicsDevice.destroyOrRetire(cleanup: () -> Unit) {
    val reaper = activeReaper
    if (reaper != null) {
        deferredDestructionCount++
        reaper.retire(cleanup)
    } else {
        if (device != 0L) com.awakekt.awake.vulkan.gen.VulkanBuffers.vkDeviceWaitIdle(device)
        cleanup()
    }
}
