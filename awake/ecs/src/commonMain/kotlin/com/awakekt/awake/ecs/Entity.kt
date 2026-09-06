/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.jvm.JvmInline

/**
 * Stable entity handle. The low 32 bits carry the sparse-set id; the high 32 bits carry
 * the generation, so a recycled id cannot alias an older handle.
 *
 * @property packed The raw 64-bit value carrying both ID and generation.
 */
@JvmInline
value class Entity(val packed: Long) {
    /** The sparse-set ID of the entity (low 32 bits). */
    val id: Int get() = packed.toInt()

    /** The generation of the entity (high 32 bits). */
    val generation: Int get() = (packed ushr GENERATION_SHIFT).toInt()

    /**
     * Returns a string representation of this entity.
     *
     * @return A string containing the ID and generation.
     */
    override fun toString(): String = "Entity(id=$id, generation=$generation)"

    /**
     * Factory for creating entity handles.
     */
    companion object {
        /** Shift amount for the generation bits. */
        private const val GENERATION_SHIFT = 32

        /** Mask for the ID bits. */
        private const val ID_MASK = 0xFFFF_FFFFL

        /**
         * Creates an [Entity] handle from an [id] and [generation].
         *
         * @param id The sparse-set ID.
         * @param generation The generation.
         * @return A new [Entity] handle.
         */
        fun of(id: Int, generation: Int): Entity {
            require(id >= 0) { "Entity id must be non-negative." }
            require(generation >= 0) { "Entity generation must be non-negative." }
            return Entity((generation.toLong() shl GENERATION_SHIFT) or (id.toLong() and ID_MASK))
        }
    }
}
