/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldtext

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.RegisteredBinding
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.worldtext.WorldText
import kotlin.reflect.KClass

/** Loads [SceneWorldText] as the entity's [WorldText], and exports what it says and how it looks now. */
@RegisteredBinding
object WorldTextBinding : SceneComponentBinding<WorldText, SceneWorldText> {
    override val componentClass: KClass<WorldText> = WorldText::class
    override val schemaClass: KClass<SceneWorldText> = SceneWorldText::class
    override val serializer = SceneWorldText.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneWorldText, context: SceneResolutionContext) {
        world.add(entity, component.toWorldText())
    }

    override fun export(world: World, entity: Entity, component: WorldText): SceneWorldText = component.toScene()
}
