/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.movement

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** Marks the entity the player moves. [speed] is in units per second; null uses the system's. */
@Serializable
@SerialName("movement_control")
data class SceneMovementControl(
    val speed: Float? = null,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (speed != null && speed <= 0f) {
            add(SceneValidationIssue(path, "movement_control.speed must be greater than 0"))
        }
    }
}

object MovementControlBinding : SceneComponentBinding<MovementControl, SceneMovementControl> {
    override val componentClass: KClass<MovementControl> = MovementControl::class
    override val schemaClass: KClass<SceneMovementControl> = SceneMovementControl::class
    override val serializer = SceneMovementControl.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneMovementControl,
        context: SceneResolutionContext,
    ) {
        world.add(entity, MovementControl().apply { speed = component.speed })
    }

    override fun export(world: World, entity: Entity, component: MovementControl): SceneMovementControl =
        SceneMovementControl(speed = component.speed)
}
