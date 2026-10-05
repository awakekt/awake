/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World

/** Where a test entity is: a stand-in for whatever places entities in an app, which the library does not know. */
internal class Placed(val position: Vec3f = Vec3f(0f, 0f, 0f), val worldMatrix: Mat4 = Mat4())

/** Places an entity by its [Placed] component, so these tests need no scene. */
internal object PlacedEntities : EmitterPlacement {
    override fun position(world: World, entity: Entity, into: Vec3f): Boolean {
        val placed = world.get<Placed>(entity) ?: return false
        into.set(placed.position)
        return true
    }

    override fun orientation(world: World, entity: Entity): Mat4? = world.get<Placed>(entity)?.worldMatrix
}
