/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.EntityNames
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsSystem

/**
 * What nodes that act on the scene reach through. [BlueprintSystem] owns one; pass it to a game node
 * that needs the world.
 */
class BlueprintScene internal constructor(private val physics: PhysicsSystem?) {
    /** The world [BlueprintSystem] runs in, set before any node runs. */
    lateinit var world: World
        private set

    /** Entity lookup by name in [world]. */
    lateinit var names: EntityNames
        private set

    /**
     * Asks the running game to play another of its scenes, by the scene's path relative to the project
     * root, as Load Scene does. The host that runs [BlueprintSystem] sets it: a played project's
     * blueprint capability passes its scene switch. Null where nothing switches scenes, such as an
     * editor playing the one scene it edits, and Load Scene then only warns.
     */
    var loadScene: ((path: String) -> Unit)? = null

    /**
     * Destroys [entity] and every node under it, its children through [Transform] and theirs, as
     * deleting a node in an editor does: a child left behind would be left at the scene's root,
     * still drawn where it was. The deepest go first, and each physics body is freed before its
     * entity so none outlives it.
     *
     * @param entity The entity to destroy, with the nodes under it.
     */
    fun destroy(entity: Entity) {
        val doomed = ArrayList<Entity>().also { it += entity }
        var next = 0
        while (next < doomed.size) {
            val parent = doomed[next++]
            world.queryEach(Transform::class) { child, transform -> if (transform.parent == parent) doomed += child }
        }
        for (index in doomed.indices.reversed()) {
            val doomedEntity = doomed[index]
            if (!world.isAlive(doomedEntity)) continue
            physics?.destroyBody(world, doomedEntity)
            world.destroy(doomedEntity)
        }
    }

    /** Whether [world] is new to this scene. */
    internal fun bind(world: World): Boolean {
        if (::world.isInitialized && this.world === world) return false
        this.world = world
        names = EntityNames(world)
        return true
    }
}
