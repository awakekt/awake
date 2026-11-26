/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.spatial

import kotlin.math.max
import kotlin.math.min

/**
 * One vertical column in [SpatialGrid]: its current member IDs in a primitive [IntArray] (zero
 * `Int` boxing) paired with its monotonically-widening `[minY, maxY]` height envelope.
 */
internal class SpatialColumn(
    var minY: Float,
    var maxY: Float,
) {
    var ids: IntArray = IntArray(INITIAL_COLUMN_CAPACITY)
        private set
    var size: Int = 0
        private set

    fun add(id: Int) {
        if (size == ids.size) ids = ids.copyOf(ids.size * 2)
        ids[size++] = id
    }

    fun remove(id: Int) {
        for (i in 0 until size) {
            if (ids[i] == id) {
                val last = size - 1
                ids[i] = ids[last]
                size = last
                return
            }
        }
    }

    fun widen(lo: Float, hi: Float) {
        minY = min(minY, lo)
        maxY = max(maxY, hi)
    }

    private companion object {
        const val INITIAL_COLUMN_CAPACITY = 4
    }
}

/**
 * Primitive open-addressing `Long -> SpatialColumn` hash table so per-frame column probes never
 * box 64-bit column keys (`HashMap<Long, ...>` allocates 24 B per lookup on JVM/Wasm).
 */
internal class LongColumnMap(initialCapacity: Int = INITIAL_CAPACITY) {
    private var keys: LongArray
    private var values: Array<SpatialColumn?>
    private var mask: Int
    private var threshold: Int
    private var usedCount: Int = 0
    private var emptyKeyValue: SpatialColumn? = null

    init {
        val cap = tableSizeFor(initialCapacity)
        keys = LongArray(cap) { EMPTY_KEY }
        values = arrayOfNulls(cap)
        mask = cap - 1
        threshold = (cap * LOAD_FACTOR).toInt()
    }

    operator fun get(key: Long): SpatialColumn? {
        if (key == EMPTY_KEY) return emptyKeyValue
        var slot = mixLong(key) and mask
        while (keys[slot] != EMPTY_KEY && keys[slot] != key) {
            slot = (slot + 1) and mask
        }
        return if (keys[slot] == key) values[slot] else null
    }

    fun getOrCreate(key: Long, minY: Float, maxY: Float): SpatialColumn {
        if (key == EMPTY_KEY) {
            return emptyKeyValue ?: SpatialColumn(minY, maxY).also { emptyKeyValue = it }
        }
        var slot = mixLong(key) and mask
        while (keys[slot] != EMPTY_KEY && keys[slot] != key) {
            slot = (slot + 1) and mask
        }
        val existing = if (keys[slot] == key) values[slot] else null
        return existing ?: SpatialColumn(minY, maxY).also { created ->
            keys[slot] = key
            values[slot] = created
            if (++usedCount >= threshold) rehash()
        }
    }

    fun clear() {
        keys.fill(EMPTY_KEY)
        values.fill(null)
        usedCount = 0
        emptyKeyValue = null
    }

    private fun rehash() {
        val oldKeys = keys
        val oldValues = values
        val newCap = oldKeys.size * 2
        val newKeys = LongArray(newCap) { EMPTY_KEY }
        val newValues = arrayOfNulls<SpatialColumn>(newCap)
        val newMask = newCap - 1
        for (i in oldKeys.indices) {
            val k = oldKeys[i]
            if (k != EMPTY_KEY) {
                var slot = mixLong(k) and newMask
                while (newKeys[slot] != EMPTY_KEY) slot = (slot + 1) and newMask
                newKeys[slot] = k
                newValues[slot] = oldValues[i]
            }
        }
        keys = newKeys
        values = newValues
        mask = newMask
        threshold = (newCap * LOAD_FACTOR).toInt()
    }

    private companion object {
        const val INITIAL_CAPACITY = 64
        const val LOAD_FACTOR = 0.65f
        const val EMPTY_KEY = Long.MIN_VALUE
        const val MIX_PRIME = -7046029254386353131L

        fun mixLong(key: Long): Int {
            val h = key * MIX_PRIME
            return (h xor (h ushr 32)).toInt()
        }
    }
}

/**
 * Primitive open-addressing `Int -> V` hash table with backward-shift deletion so per-frame entity
 * bounds lookups allocate zero boxed `Integer` keys while supporting arbitrary (including sparse
 * or negative) IDs.
 */
internal class IntEntryMap<V : Any>(initialCapacity: Int = INITIAL_CAPACITY) {
    private var keys: IntArray
    private var values: Array<Any?>
    private var mask: Int
    private var threshold: Int
    private var tableSize: Int = 0
    private var zeroValue: Any? = null

    val size: Int get() = tableSize + if (zeroValue != null) 1 else 0

    init {
        val cap = tableSizeFor(initialCapacity)
        keys = IntArray(cap)
        values = arrayOfNulls(cap)
        mask = cap - 1
        threshold = (cap * LOAD_FACTOR).toInt()
    }

    @Suppress("UNCHECKED_CAST")
    operator fun get(key: Int): V? {
        if (key == 0) return zeroValue as V?
        var slot = mixInt(key) and mask
        while (keys[slot] != 0 && keys[slot] != key) {
            slot = (slot + 1) and mask
        }
        return if (keys[slot] == key) values[slot] as V? else null
    }

    operator fun set(key: Int, value: V) {
        if (key == 0) {
            zeroValue = value
            return
        }
        var slot = mixInt(key) and mask
        while (keys[slot] != 0 && keys[slot] != key) {
            slot = (slot + 1) and mask
        }
        val inserted = keys[slot] == 0
        keys[slot] = key
        values[slot] = value
        if (inserted && ++tableSize >= threshold) rehash()
    }

    @Suppress("UNCHECKED_CAST")
    fun remove(key: Int): V? {
        if (key == 0) {
            val prev = zeroValue as V?
            zeroValue = null
            return prev
        }
        var slot = mixInt(key) and mask
        while (keys[slot] != 0 && keys[slot] != key) {
            slot = (slot + 1) and mask
        }
        val removed = if (keys[slot] == key) {
            val prev = values[slot] as V?
            shiftDelete(slot)
            tableSize--
            prev
        } else {
            null
        }
        return removed
    }

    fun clear() {
        keys.fill(0)
        values.fill(null)
        tableSize = 0
        zeroValue = null
    }

    fun keySet(): Set<Int> = KeySetView()

    private fun shiftDelete(startSlot: Int) {
        var hole = startSlot
        var cur = (hole + 1) and mask
        while (true) {
            val k = keys[cur]
            if (k == 0) {
                keys[hole] = 0
                values[hole] = null
                return
            }
            val ideal = mixInt(k) and mask
            val distCur = (cur - ideal) and mask
            val distHole = (hole - ideal) and mask
            if (distHole < distCur) {
                keys[hole] = k
                values[hole] = values[cur]
                hole = cur
            }
            cur = (cur + 1) and mask
        }
    }

    private fun rehash() {
        val oldKeys = keys
        val oldValues = values
        val newCap = oldKeys.size * 2
        val newKeys = IntArray(newCap)
        val newValues = arrayOfNulls<Any>(newCap)
        val newMask = newCap - 1
        for (i in oldKeys.indices) {
            val k = oldKeys[i]
            if (k != 0) {
                var slot = mixInt(k) and newMask
                while (newKeys[slot] != 0) slot = (slot + 1) and newMask
                newKeys[slot] = k
                newValues[slot] = oldValues[i]
            }
        }
        keys = newKeys
        values = newValues
        mask = newMask
        threshold = (newCap * LOAD_FACTOR).toInt()
    }

    private inner class KeySetView : AbstractSet<Int>() {
        override val size: Int get() = this@IntEntryMap.size
        override fun contains(element: Int): Boolean = get(element) != null
        override fun iterator(): Iterator<Int> = object : Iterator<Int> {
            private var emittedZero = zeroValue == null
            private var index = findNext(0)

            private fun findNext(from: Int): Int {
                var i = from
                while (i < keys.size && keys[i] == 0) i++
                return i
            }

            override fun hasNext(): Boolean = !emittedZero || index < keys.size

            override fun next(): Int {
                if (!emittedZero) {
                    emittedZero = true
                    return 0
                }
                if (index >= keys.size) throw NoSuchElementException()
                val k = keys[index]
                index = findNext(index + 1)
                return k
            }
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 64
        const val LOAD_FACTOR = 0.65f
        const val MIX_INT = -1640531527

        fun mixInt(key: Int): Int {
            val h = key * MIX_INT
            return h xor (h ushr 16)
        }
    }
}

private fun tableSizeFor(minCapacity: Int): Int {
    var cap = 16
    while (cap < minCapacity) cap = cap shl 1
    return cap
}
