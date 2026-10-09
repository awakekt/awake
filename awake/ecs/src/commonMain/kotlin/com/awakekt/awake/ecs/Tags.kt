/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

/**
 * What an entity is to gameplay, by role: an "enemy", a "pickup", the "checkpoint" a player passes.
 * An entity can have several, so an enemy that flies is tagged `enemy` and `flying` rather than with
 * one compound name. Find tagged entities with [withTag], and test one with [hasTag].
 *
 * A tag says what an entity is; its name says which one it is. Unrelated to [EcsTag], which marks a
 * component type that carries no data so the world can store it more cheaply.
 *
 * @param names The entity's tags, each one [isValid] accepts.
 */
class Tags(names: Set<String> = emptySet()) : Poolable {
    /** The entity's tags. Replacing the set is how to change them; each must be one [isValid] accepts. */
    var names: Set<String> = names
        set(value) {
            requireValid(value)
            field = value
        }

    init {
        requireValid(names)
    }

    /** Whether the entity is tagged [tag]. */
    operator fun contains(tag: String): Boolean = tag in names

    override fun reset() {
        names = emptySet()
    }

    /** The rule a tag follows, shared by the scene component and the project's tag list. */
    companion object {
        private val TAG = Regex("[A-Za-z0-9_][A-Za-z0-9_.-]*")

        /**
         * Whether [tag] can be a tag: letters, digits, `_`, `.` and `-`, starting with a letter, digit or
         * `_`, so it has no whitespace, inside or around it, and is never empty.
         */
        fun isValid(tag: String): Boolean = TAG.matches(tag)

        private fun requireValid(tags: Set<String>) {
            val invalid = tags.filterNot(::isValid)
            require(invalid.isEmpty()) {
                "a tag is letters, digits, '_', '.' and '-', starting with a letter, digit or '_': ${invalid.joinToString { "\"$it\"" }}"
            }
        }
    }
}

/**
 * The entities tagged [tag] when this is called, in a list of their own: destroying or retagging them
 * while going through it is safe. The order is the world's, not one to rely on.
 */
fun World.withTag(tag: String): List<Entity> = buildList {
    queryEach(Tags::class) { entity, tags -> if (tag in tags) add(entity) }
}

/** Whether [entity] is tagged [tag]; false for an entity with no [Tags]. */
fun World.hasTag(entity: Entity, tag: String): Boolean = get<Tags>(entity)?.contains(tag) == true
