/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.SpinControl

/** Built-in bi-directional binding connecting live [SpinControl] components with serializable [SceneSpinControl] components. */
object SpinControlBinding : SceneComponentBinding<SpinControl, SceneSpinControl> {
    override val componentClass = SpinControl::class

    override fun canResolve(component: SceneComponent): Boolean = component is SceneSpinControl

    override fun attach(
        world: World,
        entity: Entity,
        component: SceneComponent,
        context: SceneResolutionContext,
    ) {
        val spin = component as SceneSpinControl
        world.add(entity, spin.toComponent())
    }

    override fun export(world: World, entity: Entity, component: SpinControl): SceneSpinControl =
        component.toSceneComponent()

    override fun exportFrom(world: World, entity: Entity): SceneSpinControl? =
        world.get(entity, componentClass)?.let { export(world, entity, it) }

    /** Converts a [SceneSpinControl] component into a live [SpinControl] component. */
    fun SceneSpinControl.toComponent(): SpinControl = SpinControl().also {
        it.radians = radians
        it.speed = speed
    }

    /** Converts a live [SpinControl] component into a [SceneSpinControl] component. */
    fun SpinControl.toSceneComponent(): SceneSpinControl = SceneSpinControl(
        radians = radians,
        speed = speed,
    )
}
