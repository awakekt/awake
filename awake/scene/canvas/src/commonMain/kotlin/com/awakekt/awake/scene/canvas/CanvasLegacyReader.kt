/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlin.reflect.KClass

// How a scene or prefab saved before the split, with one `canvas_element` for each element, reads
// now: each becomes the component set that draws it the same way. A scene that loads one attaches
// the set, and saves it back in its current form.

/**
 * The components this `canvas_element` is now, in the order a node saves them, by binding name: a
 * `canvas_rect` always, the words, look, child placement, binding, following and pointer settings it
 * sets, and the widget of its kind. A field its kind never used, such as a Panel's text or a Bar's camera, is
 * left out, as it never had an effect.
 */
fun SceneCanvasElement.toCanvasComponents(): List<SceneComponent> = buildList {
    add(SceneCanvasRect(anchor, offsetX, offsetY, width, height, order, visible, touchOnly, safeArea, grow, elementId, controlId))
    val words = wordsOrNull()
    words?.let(::add)
    SceneCanvasAppearance(color, background, style, skin).takeIf { it != SceneCanvasAppearance() }?.let(::add)
    layout?.let { add(SceneCanvasLayout(it)) }
    cluster?.let { add(SceneCanvasCluster(it)) }
    bind?.let { add(SceneCanvasBind(it)) }
    // A follower pool's template sets how its followers behave before it names what they follow.
    SceneCanvasFollow(follow, followOffset, followBounds, followOutset, followRules).takeIf { it != SceneCanvasFollow() }?.let(::add)
    if (followArrow) add(SceneCanvasFollowArrow())
    SceneCanvasInput(pressable, draggable, dropTarget, wheel).takeIf { it != SceneCanvasInput() }?.let(::add)
    if (tooltip.isNotEmpty()) add(SceneCanvasTooltip(tooltip, if (words == null) textArgs else emptyMap()))
    widgetOrNull()?.let(::add)
}.sortedBy { savedOrder[it::class] }

/**
 * These components with a [moveHandle] and its [moveStrip] set on their `canvas_input`, added in its
 * saved place when there is none: settings the older `canvas_element` never had.
 */
internal fun List<SceneComponent>.withMoveHandle(moveHandle: Boolean, moveStrip: Float): List<SceneComponent> {
    val input = firstNotNullOfOrNull { it as? SceneCanvasInput }
    return when {
        !moveHandle && moveStrip == 0f -> this
        input != null -> map { if (it === input) input.copy(moveHandle = moveHandle, moveStrip = moveStrip) else it }
        else -> (this + SceneCanvasInput(moveHandle = moveHandle, moveStrip = moveStrip)).sortedBy { savedOrder[it::class] }
    }
}

/** Where a node lists each canvas component when saved: the order the canvas registry exports them in. */
private val savedOrder: Map<KClass<*>, Int> by lazy { CanvasSceneComponents.bindings.withIndex().associate { (index, binding) -> binding.schemaClass to index } }

/** This element's words: always for the kinds that show them, never for a Panel, and for the rest when it sets any. */
private fun SceneCanvasElement.wordsOrNull(): SceneCanvasText? {
    val words = SceneCanvasText(text, textKey, textArgs, fontSize, font, textAlign, links, numerals)
    return when (kind) {
        CanvasElementKind.Text, CanvasElementKind.Button, CanvasElementKind.TextField -> words
        CanvasElementKind.Panel -> null
        else -> words.takeIf { it != SceneCanvasText() }
    }
}

private fun SceneCanvasElement.widgetOrNull(): SceneComponent? = when (kind) {
    CanvasElementKind.Text, CanvasElementKind.Panel -> null
    CanvasElementKind.Button -> SceneCanvasButton(action, doubleAction, button, enabled)
    CanvasElementKind.Bar -> SceneCanvasBar(value, bar)
    CanvasElementKind.Sweep -> SceneCanvasSweep(value, sweep)
    CanvasElementKind.Stepper -> SceneCanvasStepper(stepper)
    CanvasElementKind.Joystick -> SceneCanvasJoystick(action, joystick)
    CanvasElementKind.LookArea -> SceneCanvasLookArea(action, look)
    CanvasElementKind.TextField -> SceneCanvasTextField(mask, maxLength, maxLengthUnit, placeholder, padding)
    CanvasElementKind.View -> SceneCanvasView(camera)
    CanvasElementKind.Image -> SceneCanvasImage()
}

/** This node with each `canvas_element` on it, and on every node under it, replaced by its [toCanvasComponents]. */
fun SceneNode.withCanvasComponents(): SceneNode = copy(
    components = components.flatMap { if (it is SceneCanvasElement) it.toCanvasComponents() else listOf(it) },
    children = children.map { it.withCanvasComponents() },
)

/** This prefab with every `canvas_element` replaced by its [SceneCanvasElement.toCanvasComponents]. */
fun ScenePrefab.withCanvasComponents(): ScenePrefab = copy(root = root.withCanvasComponents())

/** This scene with every `canvas_element` replaced by its [SceneCanvasElement.toCanvasComponents]. */
fun SceneDocument.withCanvasComponents(): SceneDocument = copy(nodes = nodes.map { it.withCanvasComponents() })

/**
 * The `canvas_element` a node's canvas components make, or null when the node has no `canvas_rect`
 * or `canvas_element`: what a tool written for the single component reads, such as an element's id
 * or kind. A node saved in the older form answers its own.
 */
fun SceneNode.canvasElementView(): SceneCanvasElement? = components.firstNotNullOfOrNull { it as? SceneCanvasElement }
    ?: components.firstNotNullOfOrNull { it as? SceneCanvasRect }?.let { rect ->
        components.fold(rect.toLegacy()) { element, component -> element.with(component) }
    }

private fun SceneCanvasRect.toLegacy() = SceneCanvasElement(
    kind = CanvasElementKind.Panel, anchor = anchor, offsetX = offsetX, offsetY = offsetY, width = width, height = height,
    order = order, visible = visible, touchOnly = touchOnly, safeArea = safeArea, grow = grow, elementId = elementId, controlId = controlId,
)

@Suppress("CyclomaticComplexMethod") // One branch for each component.
private fun SceneCanvasElement.with(component: SceneComponent): SceneCanvasElement = when (component) {
    is SceneCanvasText -> copy(
        kind = if (kind == CanvasElementKind.Panel) CanvasElementKind.Text else kind,
        text = component.text,
        textKey = component.textKey,
        textArgs = component.textArgs,
        fontSize = component.fontSize,
        font = component.font,
        textAlign = component.textAlign,
        links = component.links,
        numerals = component.numerals,
    )
    is SceneCanvasAppearance -> copy(color = component.color, background = component.background, style = component.style, skin = component.skin)
    is SceneCanvasLayout -> copy(layout = component.layout)
    is SceneCanvasCluster -> copy(cluster = component.cluster)
    is SceneCanvasBind -> copy(bind = component.bind)
    is SceneCanvasFollow -> copy(
        follow = component.node,
        followOffset = component.offset,
        followBounds = component.bounds,
        followOutset = component.outset,
        followRules = component.rules,
    )
    is SceneCanvasFollowArrow -> copy(followArrow = true)
    is SceneCanvasInput -> copy(pressable = component.pressable, draggable = component.draggable, dropTarget = component.dropTarget, wheel = component.wheel)
    is SceneCanvasTooltip -> copy(tooltip = component.text, textArgs = textArgs.ifEmpty { component.textArgs })
    is SceneCanvasButton -> copy(kind = CanvasElementKind.Button, action = component.action, doubleAction = component.doubleAction, button = component.settings, enabled = component.enabled)
    is SceneCanvasBar -> copy(kind = CanvasElementKind.Bar, value = component.value, bar = component.settings)
    is SceneCanvasSweep -> copy(kind = CanvasElementKind.Sweep, value = component.value, sweep = component.settings)
    is SceneCanvasStepper -> copy(kind = CanvasElementKind.Stepper, stepper = component.settings)
    is SceneCanvasJoystick -> copy(kind = CanvasElementKind.Joystick, action = component.action, joystick = component.settings)
    is SceneCanvasLookArea -> copy(kind = CanvasElementKind.LookArea, action = component.action, look = component.settings)
    is SceneCanvasTextField -> copy(
        kind = CanvasElementKind.TextField,
        mask = component.mask,
        maxLength = component.maxLength,
        maxLengthUnit = component.maxLengthUnit,
        placeholder = component.placeholder,
        padding = component.padding,
    )
    is SceneCanvasView -> copy(kind = CanvasElementKind.View, camera = component.camera)
    is SceneCanvasImage -> copy(kind = CanvasElementKind.Image)
    else -> this
}
