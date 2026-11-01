/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.character.TerrainCollider
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsBody

/**
 * The scene's terrain bodies, as [TerrainCollider]s: every [PhysicsBody] whose shape is a
 * [HeightFieldShape], once `PhysicsSystem` has built it, laid at its entity's [Transform].
 *
 * Found again when the number of physics bodies changes, while a heightfield is still waiting for
 * its body, or when a terrain found before no longer has the body it had: replaced, rebuilt or
 * gone, with the count unchanged. A scene that has settled pays a size check and one lookup per
 * terrain a step. A terrain whose transform moves after its body is built isn't followed. Terrain
 * bodies are static, and so are these.
 */
internal class SceneTerrainColliders {
    private val surfaces = ArrayList<TerrainCollider>()
    private val owners = ArrayList<Entity>()
    private var bodiesSeen = -1
    private var waiting = false

    /** The terrains found, kept up to date with [world]. */
    fun of(world: World): List<TerrainCollider> {
        val bodies = world.family(BODY)
        if (bodies.size != bodiesSeen || waiting || stale(world)) {
            bodiesSeen = bodies.size
            waiting = false
            surfaces.clear()
            owners.clear()
            bodies.forEach { entity, body ->
                val shape = body.shape as? HeightFieldShape ?: return@forEach
                val handle = body.handle
                val transform = world.get(entity, TRANSFORM)
                if (handle == null || transform == null) {
                    waiting = true
                } else {
                    surfaces += TerrainCollider(handle, shape, transform.position)
                    owners += entity
                }
            }
        }
        return surfaces
    }

    /** Whether a terrain found before has lost the body it was found with. */
    private fun stale(world: World): Boolean {
        for (index in surfaces.indices) {
            if (world.get(owners[index], BODY)?.handle != surfaces[index].handle) return true
        }
        return false
    }

    /** Whether [body] is one of the terrains found. */
    fun isTerrain(body: BodyHandle?): Boolean = body != null && surfaces.any { it.handle == body }

    private companion object {
        val BODY = PhysicsBody::class
        val TRANSFORM = Transform::class
    }
}
