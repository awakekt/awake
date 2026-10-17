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
    fun SceneCanvasElement.toComponent(): CanvasElement = CanvasElement().also { applyTo(it) }

    /**
     * Sets every saved option of [element] to this schema's, leaving what it reports at runtime, such
     * as presses, as it is: what a [CanvasFollowerPool] does to an element it hands out again.
     *
     * @param element The live element to set.
     */
    fun SceneCanvasElement.applyTo(element: CanvasElement) {
        element.kind = kind
        element.anchor = anchor
        element.offsetX = offsetX
        element.offsetY = offsetY
        element.width = width
        element.height = height
        element.text = text
        element.textKey = textKey
        element.textArgs = textArgs
        element.fontSize = fontSize
        element.color = color
        element.background = background
        element.value = value
        element.order = order
        element.visible = visible
        element.action = action
        element.doubleAction = doubleAction
        element.touchOnly = touchOnly
        element.safeArea = safeArea
        element.style = style
        element.textAlign = textAlign
        element.follow = follow
        element.followOffset = Vec3f(followOffset.x, followOffset.y, followOffset.z)
        element.followBounds = followBounds
        element.followOutset = followOutset
        element.followRules = followRules
        element.followArrow = followArrow
        element.layout = layout
        element.grow = grow
        element.bind = bind
        element.wheel = wheel
        element.draggable = draggable
        element.dropTarget = dropTarget
        element.camera = camera
        element.links = links
        element.mask = mask
        element.maxLength = maxLength
        element.maxLengthUnit = maxLengthUnit
        element.font = font
        element.pressable = pressable
        element.joystick = joystick
        element.button = button
        element.enabled = enabled
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
        followRules = followRules,
        followArrow = followArrow,
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
        joystick = joystick,
        button = button,
        enabled = enabled,
    )
}
