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
 * Eviction: a key keeps its slots until [retainBatches] batches have begun without it drawing,
 * and gives back the slots past its count once that many have begun without it reaching it. A
 * window of a batch or two gave a prop leaving and re-entering a moving view, or drawn by one of
 * two scenes a frame (a canvas view's beside the main one), whichever slot was free when it came
 * back: a textured draw then built a material group for a buffer it had never had, and a frame
 * built about 18 (#1621). So slots in use stay bounded by the draws of the last [retainBatches]
 * batches. Freed slots are taken before the pool grows, so indices stay compact; [onRelease] hears
 * each slot given back, with the material that held it. GPU-free.
 */
internal class StableDrawSlots(
    private val retainBatches: Int = RETAIN_BATCHES,
    private val onRelease: ((material: Any, slot: Int) -> Unit)? = null,
) {
    private class Held(
        val mesh: Any,
        val material: Any,
        var slots: IntArray = IntArray(1),
        var count: Int = 0,
        var used: Int = 0,
        var batch: Long = 0,
        var peak: Int = 0,
        var peakBatch: Long = 0,
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

    /** Starts a batch, giving back what has not been drawn for [retainBatches] batches. */
    fun begin() {
        batch += 1
        var index = 0
        while (index < entries.size) {
            val entry = entries[index]
            if (batch - entry.batch >= retainBatches) {
                release(entry, keep = 0)
                forget(entry)
                // Swap-remove: entries keep no order.
                entries[index] = entries[entries.size - 1]
                entries.removeAt(entries.size - 1)
                continue
            }
            if (entry.count > entry.used && batch - entry.peakBatch >= retainBatches) {
                release(entry, keep = entry.used)
                entry.peak = entry.used
                entry.peakBatch = batch
            }
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
        val slot = entry.slots[entry.used++]
        if (entry.used >= entry.peak) {
            entry.peak = entry.used
            entry.peakBatch = batch
        }
        return slot
    }

    private fun release(entry: Held, keep: Int) {
        while (entry.count > keep) {
            if (freeCount == free.size) free = free.copyOf(free.size * 2)
            val slot = entry.slots[--entry.count]
            free[freeCount++] = slot
            onRelease?.invoke(entry.material, slot)
        }
    }

    internal companion object {
        const val INITIAL = 64

        /** About ten seconds of one view at 60 frames a second, five of two. */
        const val RETAIN_BATCHES = 600
    }
}
