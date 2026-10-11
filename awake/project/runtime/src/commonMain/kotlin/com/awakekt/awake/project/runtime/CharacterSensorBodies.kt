/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.character.CharacterController
import com.awakekt.awake.scene.character.CharacterControllerSystem
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.physics.ScenePhysicsBody
import com.awakekt.awake.scene.physics.character.CharacterConfig

/**
 * Gives each character a body of its own ([CharacterConfig.innerBody]) in a scene that has a sensor.
 *
 * A character moves by sweeping its shape, which leaves the physics world nothing to report a
 * contact about, so without that body no sensor ever sees the player and a blueprint's On Sensor
 * Enter never fires for them. It runs before [CharacterControllerSystem], which builds each
 * character's controller, body included, from its config.
 */
internal class CharacterSensorBodies : System {
    private val waiting = ArrayList<Entity>()
    private val collect: (Entity, CharacterController) -> Unit = { entity, character -> if (!character.config.innerBody) waiting += entity }

    override fun update(world: World, delta: Float) {
        world.queryEach(CHARACTER, collect)
        // Nearly every step finds none, and returns without allocating.
        if (waiting.isEmpty()) return
        for (index in 0 until waiting.size) {
            val entity = waiting[index]
            val character = world.get(entity, CHARACTER) ?: continue
            world.add(entity, CharacterController(character.config.copy(innerBody = true), character.jumpSpeed, character.gravity))
        }
        waiting.clear()
    }

    private companion object {
        val CHARACTER = CharacterController::class
    }
}

/** Whether this node, or one under it, is a sensor. */
internal fun SceneNode.hasSensor(): Boolean =
    components.any { it is ScenePhysicsBody && it.sensor } || children.any { it.hasSensor() }
