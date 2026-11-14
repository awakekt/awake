/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.render.material.GpuMaterial
import com.awakekt.awake.render.mesh.GpuMesh
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.CullMode

/**
 * The order an [InstanceBatcher] emits its groups in, kept from frame to frame (#1555).
 *
 * Emitted in the order of each group's first copy this frame, a group moved whenever culling
 * removed that copy, and every draw after it moved too; a backend that keys per-draw buffers by
 * position then re-uploaded them all. Instead each group has a place, kept across frames by its
 * **batch key**: mesh and material (by identity), whether its copies are tinted per instance, cull
 * mode and alpha mode. Groups are emitted in place order; groups sharing a place (copies of one
 * mesh and material differing in another per-draw value) follow their first copies this frame.
 *
 * A place lasts while its group does: the first frame no group has its key, it is freed, so the
 * table never holds more places than the frame before had groups. A group that returns, or a new
 * one, takes a place after every other. Freeing keeps the order of the rest, so a group that
 * disappears moves only the groups after it, by its own draws.
 *
 * Its places and arrays are kept and grown, never rebuilt: a frame with the groups of the last
 * allocates nothing.
 */
internal class InstanceGroupOrder {
    private val places = ArrayList<Place>()
    private var placeCount = 0
    private var table = IntArray(INITIAL_TABLE)
    private var emitted = IntArray(INITIAL_TABLE)

    /** Live places, for tests: one per batch key the last frame emitted. */
    val size: Int get() = placeCount

    /**
     * Calls [emit] with each of [groups]' first [count], in place order, then frees the places no
     * group took this frame.
     */
    inline fun emit(groups: List<InstanceGroup>, count: Int, emit: (InstanceGroup) -> Unit) {
        if (emitted.size < count) emitted = IntArray(maxOf(count, emitted.size * 2))
        for (index in 0 until count) {
            groups[index].place = placeOf(groups[index])
            emitted[index] = index
        }
        sortByPlace(groups, count)
        for (index in 0 until count) emit(groups[emitted[index]])
        freeUnseen()
    }

    /** [emitted]'s first [count] by place, then by first copy: an insertion sort, linear when little moved. */
    @PublishedApi
    internal fun sortByPlace(groups: List<InstanceGroup>, count: Int) {
        for (index in 1 until count) {
            val moving = emitted[index]
            val place = groups[moving].place
            var at = index - 1
            while (at >= 0 && groups[emitted[at]].place > place) {
                emitted[at + 1] = emitted[at]
                at--
            }
            emitted[at + 1] = moving
        }
    }

    /** The place of [group]'s batch key, taken now if no earlier group had it. */
    @PublishedApi
    internal fun placeOf(group: InstanceGroup): Int {
        val draw = group.members[0]
        if (placeCount * 2 >= table.size) rebuildTable(table.size * 2)
        val mask = table.size - 1
        var slot = keyHash(draw.mesh, draw.material, group.tints, draw.cullMode, draw.alphaMode) and mask
        while (true) {
            val stored = table[slot]
            if (stored == 0) break
            val place = places[stored - 1]
            if (place.holds(draw.mesh, draw.material, group.tints, draw.cullMode, draw.alphaMode)) {
                place.seen = true
                return stored - 1
            }
            slot = (slot + 1) and mask
        }
        if (placeCount == places.size) places += Place()
        places[placeCount].set(draw.mesh, draw.material, group.tints, draw.cullMode, draw.alphaMode)
        table[slot] = ++placeCount
        return placeCount - 1
    }

    /** Drops the places no group took, keeping the order of the rest, and keeps the dropped ones to reuse. */
    @PublishedApi
    internal fun freeUnseen() {
        var kept = 0
        for (index in 0 until placeCount) {
            val place = places[index]
            if (!place.seen) {
                place.clear()
                continue
            }
            place.seen = false
            if (kept != index) {
                places[index] = places[kept]
                places[kept] = place
            }
            kept++
        }
        placeCount = kept
        rebuildTable(table.size)
    }

    private fun rebuildTable(size: Int) {
        if (table.size != size) table = IntArray(size) else table.fill(0)
        val mask = size - 1
        for (index in 0 until placeCount) {
            val place = places[index]
            var slot = keyHash(place.mesh, place.material, place.tints, place.cullMode, place.alphaMode) and mask
            while (table[slot] != 0) slot = (slot + 1) and mask
            table[slot] = index + 1
        }
    }

    /** One batch key and whether a group took it this frame. */
    @PublishedApi
    internal class Place {
        var mesh: GpuMesh? = null
        var material: GpuMaterial? = null
        var tints = false
        var cullMode = CullMode.None
        var alphaMode = AlphaMode.Opaque
        var seen = false

        fun holds(mesh: GpuMesh, material: GpuMaterial, tints: Boolean, cullMode: CullMode, alphaMode: AlphaMode) =
            this.mesh === mesh && this.material === material && this.tints == tints &&
                this.cullMode == cullMode && this.alphaMode == alphaMode

        fun set(mesh: GpuMesh, material: GpuMaterial, tints: Boolean, cullMode: CullMode, alphaMode: AlphaMode) {
            this.mesh = mesh
            this.material = material
            this.tints = tints
            this.cullMode = cullMode
            this.alphaMode = alphaMode
            seen = true
        }

        /** Lets go of the mesh and material, so a freed place keeps neither alive. */
        fun clear() {
            mesh = null
            material = null
        }
    }

    private companion object {
        const val INITIAL_TABLE = 256

        fun keyHash(mesh: Any?, material: Any?, tints: Boolean, cullMode: CullMode, alphaMode: AlphaMode): Int {
            val hash = (
                ((mesh.hashCode() * 31 + material.hashCode()) * 31 + cullMode.hashCode()) * 31 +
                    alphaMode.hashCode()
                ) * 2 + if (tints) 1 else 0
            // Spread the identity hashes' high bits into the low ones the mask keeps.
            return hash xor (hash ushr 16)
        }
    }
}
