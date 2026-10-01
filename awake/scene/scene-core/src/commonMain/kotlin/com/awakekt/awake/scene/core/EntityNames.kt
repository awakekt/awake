/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World

/**
 * Finds entities in [world] by their [Name] without scanning the world for every lookup.
 *
 * The world reports no component changes and [Name.value] is a plain `var`, so this cannot be kept
 * up to date as names change. Instead it checks every hit when it is used: the entity must still be
 * alive and still carry that name. A stale hit, or a miss, rebuilds the index from the [Name]
 * family once and looks again. So a name that exists costs a map lookup, and renames and destroyed
 * entities are never reported. A name that exists nowhere costs one scan per lookup; do not look one
 * up every frame.
 *
 * Names are unique within one scene document. When loaded scenes share a name, the first entity the
 * [Name] family yields wins.
 *
 * @param world The active ECS world queried for named entities.
 */
class EntityNames(private val world: World) {
    private val byName = HashMap<String, Entity>()

    /** How many times the index has been rebuilt, for tests that assert a hit does not scan. */
    internal var rebuilds = 0
        private set

    /**
     * The live entity named [name], or `null`.
     *
     * @param name The target entity name to look up.
     * @return The matching live [Entity], or `null` if not found.
     */
    fun find(name: String): Entity? {
        val cached = byName[name]
        if (cached != null && isNamed(cached, name)) return cached
        rebuild()
        return byName[name]
    }

    private fun isNamed(entity: Entity, name: String): Boolean =
        world.isAlive(entity) && world.get<Name>(entity)?.value == name

    private fun rebuild() {
        rebuilds++
        byName.clear()
        world.queryEach(Name::class) { entity, name ->
            if (name.value !in byName) byName[name.value] = entity
        }
    }
}
