/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

/**
 * Which pooled slot (a uniform buffer and its group, an instance buffer) each draw of a batch
 * takes, by what the draw is rather than where it falls in the list.
 *
 * Numbered by position, a draw culled or reordered earlier in the list moved every draw after it
 * to another slot: each found someone else's block there and was packed and uploaded again, and
 * every bind changed. Here a draw is known by its mesh and material and, among draws sharing both,
 * by its order (the [n]th such draw): the same draw finds the same slot frame after frame, with
 * its block and its instances still in it.
 *
 * Eviction: when a batch begins, a key the previous batch did not draw gives its slots back, and
 * one it drew fewer times than it holds slots gives back the slots past that count, so slots in
 * use never exceed the draws of two consecutive batches. Freed slots are taken before the pool grows, so indices stay
 * compact. GPU-free.
 */
internal class StableDrawSlots {
    private class Held(
        val mesh: Any,
        val material: Any,
        var slots: IntArray = IntArray(1),
        var count: Int = 0,
        var used: Int = 0,
        var batch: Long = 0,
    )

    private val held = HashMap<Any, HashMap<Any, Held>>()

    /** Every held entry, walked by index each batch: iterating the maps made an iterator per mesh (#1555). */
    private val entries = ArrayList<Held>()
    private var free = IntArray(INITIAL)
    private var freeCount = 0
    private var next = 0
    private var batch = 0L

    /** Slots handed out and not given back: for tests. */
    val inUse: Int get() = next - freeCount

    /** Starts a batch, giving back what the previous batch did not draw. */
    fun begin() {
        batch += 1
        var index = 0
        while (index < entries.size) {
            val entry = entries[index]
            when {
                batch - entry.batch >= 2 -> {
                    release(entry, keep = 0)
                    forget(entry)
                    // Swap-remove: entries keep no order.
                    entries[index] = entries[entries.size - 1]
                    entries.removeAt(entries.size - 1)
                    continue
                }
                entry.batch == batch - 1 -> release(entry, keep = entry.used)
            }
            entry.used = 0
            index++
        }
    }

    private fun forget(entry: Held) {
        val byMaterial = held[entry.mesh] ?: return
        byMaterial.remove(entry.material)
        if (byMaterial.isEmpty()) held.remove(entry.mesh)
    }

    /** The slot of the next draw of [mesh] with [material] in this batch. */
    fun slotFor(mesh: Any, material: Any): Int {
        val entry = held.getOrPut(mesh) { HashMap() }.getOrPut(material) { Held(mesh, material).also { entries += it } }
        if (entry.batch != batch) {
            entry.batch = batch
            entry.used = 0
        }
        if (entry.used == entry.count) {
            if (entry.count == entry.slots.size) entry.slots = entry.slots.copyOf(entry.count * 2)
            entry.slots[entry.count++] = if (freeCount > 0) free[--freeCount] else next++
        }
        return entry.slots[entry.used++]
    }

    private fun release(entry: Held, keep: Int) {
        while (entry.count > keep) {
            if (freeCount == free.size) free = free.copyOf(free.size * 2)
            free[freeCount++] = entry.slots[--entry.count]
        }
    }

    private companion object {
        const val INITIAL = 64
    }
}
