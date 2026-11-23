/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

import com.awakekt.awake.ecs.ComponentTypeId
import com.awakekt.awake.ecs.EntityList
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
 * Found again when the physics-body family changes, while a heightfield is still waiting for
 * its body, or when a terrain found before no longer has the body it had: replaced, rebuilt or
 * gone. A scene that has settled pays a family-change check and direct type-id lookups per
 * terrain a step. A terrain whose transform moves after its body is built isn't followed. Terrain
 * bodies are static, and so are these.
 */
internal class SceneTerrainColliders {
    private val surfaces = ArrayList<TerrainCollider>()
    private val owners = EntityList()
    private var cachedWorld: World? = null
    private var cachedGeneration = -1
    private var bodyTypeId = ComponentTypeId(0)
    private var transformTypeId = ComponentTypeId(0)
    private var changesSeen = -1
    private var waiting = false

    private fun resolveTypes(world: World) {
        if (cachedWorld === world && cachedGeneration == world.generation) return
        bodyTypeId = world.typeId(BODY)
        transformTypeId = world.typeId(TRANSFORM)
        cachedWorld = world
        cachedGeneration = world.generation
        changesSeen = -1
    }

    /** The terrains found, kept up to date with [world]. */
    fun of(world: World): List<TerrainCollider> {
        resolveTypes(world)
        val bodies = world.family(BODY)
        if (bodies.changes != changesSeen || waiting || stale(world)) {
            changesSeen = bodies.changes
            waiting = false
            surfaces.clear()
            owners.clear()
            bodies.forEach { entity, body ->
                val shape = body.shape as? HeightFieldShape ?: return@forEach
                val handle = body.handle
                val transform = world.get<Transform>(entity, transformTypeId)
                if (handle == null || transform == null) {
                    waiting = true
                } else {
                    surfaces += TerrainCollider(handle, shape, transform.position)
                    owners.add(entity)
                }
            }
        }
        return surfaces
    }

    /** Whether a terrain found before has lost the body it was found with. */
    private fun stale(world: World): Boolean {
        for (index in surfaces.indices) {
            if (world.get<PhysicsBody>(owners[index], bodyTypeId)?.handle != surfaces[index].handle) return true
        }
        return false
    }

    /** Whether [body] is one of the terrains found. */
    fun isTerrain(body: BodyHandle?): Boolean {
        if (body == null) return false
        var found = false
        for (index in surfaces.indices) {
            if (surfaces[index].handle == body) {
                found = true
                break
            }
        }
        return found
    }

    private companion object {
        val BODY = PhysicsBody::class
        val TRANSFORM = Transform::class
    }
}
