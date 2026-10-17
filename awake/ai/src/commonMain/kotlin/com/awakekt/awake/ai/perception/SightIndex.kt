/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlin.math.floor

/** A growable list of Ints that keeps its storage when cleared. */
internal class IntBuffer {
    var size: Int = 0
        private set
    private var values = IntArray(INITIAL)

    operator fun get(index: Int): Int = values[index]

    fun add(value: Int) {
        if (size == values.size) values = values.copyOf(size * 2)
        values[size++] = value
    }

    fun clear() {
        size = 0
    }

    private companion object {
        const val INITIAL = 8
    }
}

/**
 * Where every perceivable entity is this update, bucketed into square cells on the ground plane, so a
 * look reads only the cells within its reach. Rebuilt once per update, on the first look; with
 * [cellSize] 0, or a reach covering more cells than there are entities, a look reads them all.
 *
 * Candidates come in a fixed order, cell by cell, so a look sees the same things the same way on
 * every run.
 */
internal class SightIndex(private val cellSize: Float) {
    private var size = 0
    private var entities = LongArray(INITIAL)
    private var xs = FloatArray(INITIAL)
    private var ys = FloatArray(INITIAL)
    private var zs = FloatArray(INITIAL)
    private val perceivables = ArrayList<Perceivable>()
    private val cells = HashMap<Long, IntBuffer>()

    /** Whether it holds this update's positions. */
    var built: Boolean = false
        private set

    fun invalidate() {
        built = false
    }

    fun entity(index: Int): Entity = Entity(entities[index])

    fun perceivable(index: Int): Perceivable = perceivables[index]

    fun positionOf(index: Int, into: Vec3f): Vec3f = into.set(xs[index], ys[index], zs[index])

    fun rebuild(world: World, space: PerceptionSpace, scratch: Vec3f) {
        size = 0
        perceivables.clear()
        world.family<Perceivable>().forEach { entity, perceivable ->
            if (space.position(world, entity, scratch)) add(entity, perceivable, scratch)
        }
        // Cells a crowd has left stay empty in the map; drop them when they outnumber the crowd.
        if (cells.size > size * STALE_CELLS) cells.clear()
        for (bucket in cells.values) bucket.clear()
        if (cellSize > 0f) {
            for (i in 0 until size) cells.getOrPut(key(cell(xs[i]), cell(zs[i]))) { IntBuffer() }.add(i)
        }
        built = true
    }

    /** Fills [into] with the indices that may lie within [reach] of ([x], [z]), in a fixed order. */
    fun near(x: Float, z: Float, reach: Float, into: IntBuffer) {
        into.clear()
        val minX = cell(x - reach)
        val maxX = cell(x + reach)
        val minZ = cell(z - reach)
        val maxZ = cell(z + reach)
        val span = (maxX.toLong() - minX + 1) * (maxZ.toLong() - minZ + 1)
        if (cellSize <= 0f || !reach.isFinite() || span > size) {
            for (i in 0 until size) into.add(i)
            return
        }
        for (cx in minX..maxX) {
            for (cz in minZ..maxZ) {
                val bucket = cells[key(cx, cz)] ?: continue
                for (j in 0 until bucket.size) into.add(bucket[j])
            }
        }
    }

    private fun add(entity: Entity, perceivable: Perceivable, at: Vec3f) {
        if (size == entities.size) {
            val grown = size * 2
            entities = entities.copyOf(grown)
            xs = xs.copyOf(grown)
            ys = ys.copyOf(grown)
            zs = zs.copyOf(grown)
        }
        entities[size] = entity.packed
        xs[size] = at.x
        ys[size] = at.y
        zs[size] = at.z
        perceivables += perceivable
        size++
    }

    private fun cell(coordinate: Float): Int = if (cellSize > 0f) floor(coordinate / cellSize).toInt() else 0

    private fun key(cx: Int, cz: Int): Long = (cx.toLong() shl Int.SIZE_BITS) or (cz.toLong() and LOW_BITS)

    private companion object {
        const val INITIAL = 16
        const val STALE_CELLS = 4
        const val LOW_BITS = 0xFFFF_FFFFL
    }
}
