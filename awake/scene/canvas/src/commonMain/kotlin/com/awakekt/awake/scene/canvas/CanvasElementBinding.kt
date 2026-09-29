/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

/** Loads and saves [CanvasElement]s as `canvas_element` scene components. */
object CanvasElementBinding : SceneComponentBinding<CanvasElement, SceneCanvasElement> {
    override val componentClass: KClass<CanvasElement> = CanvasElement::class
    override val schemaClass: KClass<SceneCanvasElement> = SceneCanvasElement::class
    override val serializer = SceneCanvasElement.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneCanvasElement, context: SceneResolutionContext) {
        world.add(entity, component.toComponent())
    }

    override fun export(world: World, entity: Entity, component: CanvasElement): SceneCanvasElement =
        component.toSceneComponent()

    fun SceneCanvasElement.toComponent(): CanvasElement = CanvasElement().also {
        it.kind = kind
        it.anchor = anchor
        it.offsetX = offsetX
        it.offsetY = offsetY
        it.width = width
        it.height = height
        it.text = text
        it.fontSize = fontSize
        it.color = color
        it.background = background
        it.value = value
        it.order = order
        it.visible = visible
    }

    fun CanvasElement.toSceneComponent(): SceneCanvasElement = SceneCanvasElement(
        kind = kind,
        anchor = anchor,
        offsetX = offsetX,
        offsetY = offsetY,
        width = width,
        height = height,
        text = text,
        fontSize = fontSize,
        color = color,
        background = background,
        value = value,
        order = order,
        visible = visible,
    )
}
