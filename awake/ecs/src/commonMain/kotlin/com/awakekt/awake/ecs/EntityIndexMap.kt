/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

/**
 * A growable `entity id -> dense index` map: the sparse half of the sparse-set pattern.
 * Extracted because `ComponentStore`, `Family1Cache`, and `Family2Cache` each used to
 * hand-roll an identical copy of this (sparse array + grow-on-demand + `ABSENT` sentinel).
 *
 * Owns only the sparse array -- callers own their own dense array(s) (entities, plus
 * whatever component payload(s) they're indexing) and are responsible for calling [set]
 * whenever they place or move an entity in their dense storage, mirroring their own
 * swap-remove logic.
 *
 * Paged: ids are held in pages of [PAGE_SIZE], made the first time an id in one is set, so a set
 * holding a few entities of a large world holds the pages those ids fall in, not one slot for every
 * id up to the highest. One array per set or family sized to the highest id held 13 MiB of a
 * populated scene's live heap (#1753), most of it [ABSENT]. Reading an id costs one more array load.
 */
internal class EntityIndexMap {
    private var pages = arrayOfNulls<IntArray>(0)

    /** Returns the dense index for [id], or [ABSENT] if never set (or out of range).
     * Deliberately not `getOrNull(id) ?: ABSENT` -- [IntArray.getOrNull] boxes to `Int?` on
     * every call, which profiling showed as the single largest cost (36% of CPU samples) in
     * family-cache churn, since this runs on every add/remove. */
    fun get(id: Int): Int {
        if (id < 0) return ABSENT
        val page = id ushr PAGE_SHIFT
        if (page >= pages.size) return ABSENT
        val slots = pages[page] ?: return ABSENT
        return slots[id and PAGE_MASK]
    }

    /** Records that [id] now lives at [denseIndex] in the caller's dense storage. */
    fun set(id: Int, denseIndex: Int) {
        pageFor(id)[id and PAGE_MASK] = denseIndex
    }

    /** Clears any dense index recorded for [id]. */
    fun remove(id: Int) {
        if (id < 0) return
        val page = id ushr PAGE_SHIFT
        if (page < pages.size) pages[page]?.set(id and PAGE_MASK, ABSENT)
    }

    fun clear() {
        for (slots in pages) slots?.fill(ABSENT)
    }

    /** The page [id] falls in, made, and the page table grown, on first use. */
    private fun pageFor(id: Int): IntArray {
        val page = id ushr PAGE_SHIFT
        if (page >= pages.size) pages = pages.copyOf(maxOf(page + 1, maxOf(DEFAULT_PAGES, pages.size * CAPACITY_GROWTH_FACTOR)))
        return pages[page] ?: IntArray(PAGE_SIZE).also {
            it.fill(ABSENT)
            pages[page] = it
        }
    }

    companion object {
        const val ABSENT = -1
        private const val PAGE_SHIFT = 6

        /** Ids a page holds: 256 bytes, so a few scattered entities cost a few small pages. */
        internal const val PAGE_SIZE = 1 shl PAGE_SHIFT
        private const val PAGE_MASK = PAGE_SIZE - 1
        private const val DEFAULT_PAGES = 4
        private const val CAPACITY_GROWTH_FACTOR = 2
    }
}
