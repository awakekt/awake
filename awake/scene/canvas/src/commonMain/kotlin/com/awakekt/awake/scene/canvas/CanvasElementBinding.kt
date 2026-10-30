/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.RegisteredBinding
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasFollowArrow
import com.awakekt.awake.ui.canvas.CanvasImageWidget
import com.awakekt.awake.ui.canvas.CanvasPointerState
import com.awakekt.awake.ui.canvas.CanvasRect
import com.awakekt.awake.ui.canvas.CanvasStepper
import com.awakekt.awake.ui.canvas.CanvasViewWidget
import com.awakekt.awake.ui.canvas.CanvasWidget
import kotlin.reflect.KClass

// Where a scene file's older `canvas_element` is read: it becomes the component set that draws it
// the same way, each component loaded as its own binding loads it, so a scene saved either way loads
// to the same element. Nothing is saved in this form any more: each component's binding saves it.
// `CanvasElementExposureTest` checks the legacy options; new options belong to focused components.

/**
 * Reads the older single `canvas_element` scene component, as the canvas components it is now. It
 * reshapes the saved data rather than copying fields, so it is written by hand and listed in
 * CanvasSceneComponents with `@RegisteredBinding`.
 */
@RegisteredBinding
object CanvasElementBinding : SceneComponentBinding<CanvasElement, SceneCanvasElement> {
    override val componentClass: KClass<CanvasElement> = CanvasElement::class
    override val schemaClass: KClass<SceneCanvasElement> = SceneCanvasElement::class
    override val serializer = SceneCanvasElement.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneCanvasElement, context: SceneResolutionContext) {
        for (part in component.toCanvasComponents()) world.attachCanvasComponent(entity, part.toCanvasPart())
    }

    /** Never saves a `canvas_element`: each of the element's components saves itself. */
    override fun export(world: World, entity: Entity, component: CanvasElement): SceneCanvasElement? = null

    /**
     * Converts this schema document representation to a runtime [CanvasElement].
     *
     * @return A new element made of this element's components.
     */
    fun SceneCanvasElement.toComponent(): CanvasElement = emptyCanvasElement().also { applyTo(it) }

    /**
     * Sets every saved component of [element] to this schema's, in place of the ones it had, leaving
     * what it reports at runtime, its [CanvasPointerState], as it is: what a [CanvasFollowerPool] does
     * to an element it hands out again.
     *
     * @param element The live element to set.
     */
    fun SceneCanvasElement.applyTo(element: CanvasElement) {
        for (part in element.components()) {
            if (part !is CanvasRect && part !is CanvasPointerState) element.remove(if (part is CanvasWidget) CanvasWidget::class else part::class)
        }
        for (part in toCanvasComponents()) element.set(part.toCanvasPart())
    }

    /**
     * The older `canvas_element` form of this live element, for a tool written for it: its kind and
     * the options that existed in that form. New focused-component settings, such as View coverage
     * and masking, require the normal scene/world export rather than this legacy snapshot.
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
        numerals = numerals,
        mask = mask,
        maxLength = maxLength,
        maxLengthUnit = maxLengthUnit,
        placeholder = placeholder,
        padding = padding,
        tooltip = tooltip,
        font = font,
        pressable = pressable,
        joystick = joystick,
        button = button,
        enabled = enabled,
        look = look,
        cluster = cluster,
        skin = skin,
        controlId = controlId,
        elementId = elementId,
        bar = bar,
        sweep = sweep,
        // The live number is saved as where the stepper starts.
        stepper = if (stepper != null || number != CanvasStepper.Default.value) (stepper ?: CanvasStepper.Default).copy(value = number) else null,
    )
}

/**
 * The live component this saved canvas component loads as, by the mapping its binding uses.
 *
 * @throws IllegalArgumentException when it is not a canvas element component.
 */
@Suppress("CyclomaticComplexMethod") // One branch for each component.
internal fun SceneComponent.toCanvasPart(): Any = when (this) {
    is SceneCanvasRect -> CanvasRectBinding.run { toComponent() }
    is SceneCanvasText -> CanvasTextBinding.run { toComponent() }
    is SceneCanvasAppearance -> CanvasAppearanceBinding.run { toComponent() }
    is SceneCanvasLayout -> layout
    is SceneCanvasCluster -> cluster
    is SceneCanvasBind -> bind
    is SceneCanvasFollow -> CanvasFollowBinding.run { toComponent() }
    is SceneCanvasFollowArrow -> CanvasFollowArrowBinding.run { toComponent() }
    is SceneCanvasInput -> CanvasInputBinding.run { toComponent() }
    is SceneCanvasTooltip -> CanvasTooltipBinding.run { toComponent() }
    is SceneCanvasParticles -> CanvasParticlesBinding.run { toComponent() }
    is SceneCanvasButton -> CanvasButtonBinding.run { toComponent() }
    is SceneCanvasBar -> CanvasBarBinding.run { toComponent() }
    is SceneCanvasSweep -> CanvasSweepBinding.run { toComponent() }
    is SceneCanvasStepper -> CanvasStepperBinding.run { toComponent() }
    is SceneCanvasJoystick -> CanvasJoystickBinding.run { toComponent() }
    is SceneCanvasLookArea -> CanvasLookAreaBinding.run { toComponent() }
    is SceneCanvasTextField -> CanvasTextFieldBinding.run { toComponent() }
    is SceneCanvasView -> CanvasViewBinding.run { toComponent() }
    is SceneCanvasImage -> CanvasImageBinding.run { toComponent() }
    else -> throw IllegalArgumentException("${this::class.simpleName} is not a canvas element component")
}
