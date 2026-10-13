/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.ecs.Entity
import kotlin.math.max

/**
 * The `presses` count each canvas element had when last looked at, by entity id, so a reader sees
 * each press once and takes it from no other reader.
 *
 * An element not looked at before counts from none, as a new element does, so a press made before
 * the first look is still seen.
 */
internal class PressCounts {
    private var entities = LongArray(INITIAL_ELEMENTS)
    private var seen = IntArray(INITIAL_ELEMENTS)

    /** The presses [entity]'s element has had since the last look, given its count now. */
    fun take(entity: Entity, presses: Int): Int {
        val id = entity.id
        if (id < 0) return 0
        if (id >= seen.size) grow(id + 1)
        // An unused slot holds zeros, the same as a reset one, so a match by chance changes nothing.
        if (entities[id] != entity.packed) {
            entities[id] = entity.packed
            seen[id] = 0
        }
        val fresh = presses - seen[id]
        seen[id] = presses
        return if (fresh > 0) fresh else 0
    }

    /** Forgets every element, for a new world. */
    fun clear() {
        entities.fill(0L)
        seen.fill(0)
    }

    private fun grow(size: Int) {
        val capacity = max(size, seen.size * 2)
        entities = entities.copyOf(capacity)
        seen = seen.copyOf(capacity)
    }
}

private const val INITIAL_ELEMENTS = 16
