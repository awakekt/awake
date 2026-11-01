/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.character.TerrainSurface
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsBody

/**
 * The scene's terrain bodies, as [TerrainSurface]s: every [PhysicsBody] whose shape is a
 * [HeightFieldShape], once `PhysicsSystem` has built it, laid at its entity's [Transform].
 *
 * Found again only when the number of physics bodies changes, or while a heightfield is still
 * waiting for its body, so a scene that has settled pays one size check a step. A terrain whose
 * transform moves after its body is built isn't followed. Terrain bodies are static, and so are
 * these.
 */
internal class SceneTerrainSurfaces {
    private val surfaces = ArrayList<TerrainSurface>()
    private var bodiesSeen = -1
    private var waiting = false

    /** The terrains found, kept up to date with [world]. */
    fun of(world: World): List<TerrainSurface> {
        val bodies = world.family(BODY)
        if (bodies.size != bodiesSeen || waiting) {
            bodiesSeen = bodies.size
            waiting = false
            surfaces.clear()
            bodies.forEach { entity, body ->
                val shape = body.shape as? HeightFieldShape ?: return@forEach
                val handle = body.handle
                val transform = world.get(entity, TRANSFORM)
                if (handle == null || transform == null) {
                    waiting = true
                } else {
                    surfaces += TerrainSurface(handle, shape, transform.position)
                }
            }
        }
        return surfaces
    }

    /** Whether [body] is one of the terrains found. */
    fun isTerrain(body: BodyHandle?): Boolean = body != null && surfaces.any { it.handle == body }

    private companion object {
        val BODY = PhysicsBody::class
        val TRANSFORM = Transform::class
    }
}
