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
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** Scene form of [StaticTransform]: `{ "component": "static_transform" }`. */
@Serializable
@SerialName("static_transform")
data object SceneStaticTransform : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false
}

/** Attaches [StaticTransform] for [SceneStaticTransform], and exports it back. */
object StaticTransformBinding : SceneComponentBinding<StaticTransform, SceneStaticTransform> {
    override val componentClass: KClass<StaticTransform> = StaticTransform::class
    override val schemaClass: KClass<SceneStaticTransform> = SceneStaticTransform::class
    override val serializer = SceneStaticTransform.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneStaticTransform, context: SceneResolutionContext) {
        world.add(entity, StaticTransform)
    }

    override fun export(world: World, entity: Entity, component: StaticTransform): SceneStaticTransform = SceneStaticTransform
}
