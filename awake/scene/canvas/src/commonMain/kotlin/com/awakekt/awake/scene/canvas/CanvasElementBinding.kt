/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.ui.canvas.CanvasElement
import kotlin.reflect.KClass

// Where a scene file's `canvas_element` becomes the live [CanvasElement]'s settings, and back. It is
// the only place the two meet, so a capability option added to either side is wrong until it is
// handled here: `CanvasElementExposureTest` fails when a [CanvasElement] option is neither set from
// this mapping nor listed there as set from code only.

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

    /**
     * Converts this schema document representation to a runtime ECS [CanvasElement] component.
     *
     * @return The hydrated runtime [CanvasElement].
     */
    fun SceneCanvasElement.toComponent(): CanvasElement = CanvasElement().also {
        it.kind = kind
        it.anchor = anchor
        it.offsetX = offsetX
        it.offsetY = offsetY
        it.width = width
        it.height = height
        it.text = text
        it.textKey = textKey
        it.textArgs = textArgs
        it.fontSize = fontSize
        it.color = color
        it.background = background
        it.value = value
        it.order = order
        it.visible = visible
        it.action = action
        it.doubleAction = doubleAction
        it.touchOnly = touchOnly
        it.safeArea = safeArea
        it.style = style
        it.textAlign = textAlign
        it.follow = follow
        it.followOffset = Vec3f(followOffset.x, followOffset.y, followOffset.z)
        it.followBounds = followBounds
        it.followOutset = followOutset
        it.layout = layout
        it.grow = grow
        it.bind = bind
        it.wheel = wheel
        it.draggable = draggable
        it.dropTarget = dropTarget
        it.camera = camera
        it.links = links
        it.mask = mask
        it.maxLength = maxLength
        it.maxLengthUnit = maxLengthUnit
        it.font = font
        it.pressable = pressable
    }

    /**
     * Serializes this runtime ECS [CanvasElement] component to a scene document [SceneCanvasElement].
     *
     * @return The serializable [SceneCanvasElement] representation.
     */
    fun CanvasElement.toSceneComponent(): SceneCanvasElement = SceneCanvasElement(
        kind = kind,
        anchor = anchor,
        offsetX = offsetX,
        offsetY = offsetY,
        width = width,
        height = height,
        text = text,
        textKey = textKey,
        textArgs = textArgs,
        fontSize = fontSize,
        color = color,
        background = background,
        value = value,
        order = order,
        visible = visible,
        action = action,
        doubleAction = doubleAction,
        touchOnly = touchOnly,
        safeArea = safeArea,
        style = style,
        textAlign = textAlign,
        follow = follow,
        followOffset = SceneVec3(followOffset.x, followOffset.y, followOffset.z),
        followBounds = followBounds,
        followOutset = followOutset,
        layout = layout,
        grow = grow,
        bind = bind,
        wheel = wheel,
        draggable = draggable,
        dropTarget = dropTarget,
        camera = camera,
        links = links,
        mask = mask,
        maxLength = maxLength,
        maxLengthUnit = maxLengthUnit,
        font = font,
        pressable = pressable,
    )
}
