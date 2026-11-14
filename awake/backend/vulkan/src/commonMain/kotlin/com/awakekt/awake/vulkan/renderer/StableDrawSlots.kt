/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

/**
 * Which pooled slot (a material's uniform slot, an instance buffer) each draw of a batch takes, by
 * what the draw is rather than where it falls in the list.
 *
 * Numbered by position, a draw culled or reordered earlier in the list moved every draw after it
 * to another slot: each found someone else's block there and was packed and written again, and
 * every descriptor set or buffer bind changed. Here a draw is known by its mesh and a second key
 * (material for renderer-wide instance pools, [SlotKind] within one material's uniform slots) and,
 * among draws sharing both, by its order (the `n`th such draw): the same draw finds the same slot
 * frame after frame, with its block and its instances still in it.
 *
 * Eviction: when a batch begins, a key the previous batch did not draw gives its slots back, and
 * one it drew fewer times than it holds slots gives back the slots past that count, so slots in
 * use never exceed the draws of two consecutive batches. Freed slots are taken before the pool
 * grows, so indices stay compact. GPU-free.
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

/**
 * Per-material [StableDrawSlots]: on Vulkan each material owns its own uniform slot table indexed
 * from 0, so slot indices must stay compact within each material rather than renderer-wide.
 */
internal class StableMaterialSlots {
    private class Entry(val material: Any, val slots: StableDrawSlots)

    private val byMaterial = HashMap<Any, StableDrawSlots>()
    private val entries = ArrayList<Entry>()

    /** Starts a batch across every material, dropping materials that hold no slots in use. */
    fun begin() {
        var index = 0
        while (index < entries.size) {
            val entry = entries[index]
            entry.slots.begin()
            if (entry.slots.inUse == 0) {
                byMaterial.remove(entry.material)
                entries[index] = entries[entries.size - 1]
                entries.removeAt(entries.size - 1)
                continue
            }
            index++
        }
    }

    /** The next uniform slot in [material] for a draw of [mesh] and [kind]. */
    fun slotFor(material: Any, mesh: Any, kind: SlotKind): Int {
        val slots = byMaterial.getOrPut(material) {
            StableDrawSlots().also { entries += Entry(material, it) }
        }
        return slots.slotFor(mesh, kind)
    }

    /** The next uniform slot in [material] for a draw of [mesh], partitioned by [instanced] and [edges]. */
    fun slotFor(material: Any, mesh: Any, instanced: Boolean, edges: Boolean): Int =
        slotFor(
            material,
            mesh,
            when {
                edges -> SlotKind.Edge
                instanced -> SlotKind.Instanced
                else -> SlotKind.Single
            },
        )
}

/** Partition within one material's uniform slots so single, instanced, and wireframe-edge draws never share a slot. */
internal enum class SlotKind {
    Single,
    Instanced,
    Edge,
}

/**
 * Whether [other] holds exactly these floats, bit for bit: `==` on floats calls `-0` and `0` the
 * same, and a NaN unequal to itself.
 */
internal fun FloatArray.sameBitsAs(other: FloatArray): Boolean {
    if (size != other.size) return false
    var index = 0
    while (index < size && this[index].toRawBits() == other[index].toRawBits()) index++
    return index == size
}
