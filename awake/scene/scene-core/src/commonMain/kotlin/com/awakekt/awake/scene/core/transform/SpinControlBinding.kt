/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.transform

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

/**
 * Built-in bi-directional binding connecting live [SpinControl] components with
 * serializable [SceneSpinControl] document components.
 */
object SpinControlBinding : SceneComponentBinding<SpinControl, SceneSpinControl> {
    override val componentClass: KClass<SpinControl> = SpinControl::class
    override val schemaClass: KClass<SceneSpinControl> = SceneSpinControl::class
    override val serializer = SceneSpinControl.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneSpinControl,
        context: SceneResolutionContext,
    ) {
        world.add(entity, component.toComponent())
    }

    override fun export(world: World, entity: Entity, component: SpinControl): SceneSpinControl =
        component.toSceneComponent()

    fun SceneSpinControl.toComponent(): SpinControl = SpinControl().also {
        it.radians = radians
        it.speed = speed
    }

    fun SpinControl.toSceneComponent(): SceneSpinControl = SceneSpinControl(
        radians = radians,
        speed = speed,
    )
}
