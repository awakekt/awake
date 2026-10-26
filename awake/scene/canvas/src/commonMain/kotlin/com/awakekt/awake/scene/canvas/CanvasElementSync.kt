/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("MatchingDeclarationName") // Named for what it does: the link is the one class it needs.

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ui.canvas.CanvasAppearance
import com.awakekt.awake.ui.canvas.CanvasBarWidget
import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.CanvasButtonWidget
import com.awakekt.awake.ui.canvas.CanvasCluster
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasFollow
import com.awakekt.awake.ui.canvas.CanvasFollowArrow
import com.awakekt.awake.ui.canvas.CanvasImageWidget
import com.awakekt.awake.ui.canvas.CanvasInput
import com.awakekt.awake.ui.canvas.CanvasJoystickWidget
import com.awakekt.awake.ui.canvas.CanvasLayout
import com.awakekt.awake.ui.canvas.CanvasLookAreaWidget
import com.awakekt.awake.ui.canvas.CanvasPointerState
import com.awakekt.awake.ui.canvas.CanvasRect
import com.awakekt.awake.ui.canvas.CanvasStepperWidget
import com.awakekt.awake.ui.canvas.CanvasSweepWidget
import com.awakekt.awake.ui.canvas.CanvasText
import com.awakekt.awake.ui.canvas.CanvasTextFieldWidget
import com.awakekt.awake.ui.canvas.CanvasTooltip
import com.awakekt.awake.ui.canvas.CanvasViewWidget
import com.awakekt.awake.ui.canvas.CanvasWidget
import kotlin.reflect.KClass

// A canvas entity's components are the canvas element: what a scene loads, saves and queries. The
// entity also keeps the CanvasElement the overlay draws, made of those same component instances.
// The two are kept in step here. A change made through the element, which bumps its
// componentsVersion, is pushed into the world; anything else, a component added, replaced or
// removed on the entity, is pulled into the element.

/** The [CanvasElement.componentsVersion] the entity's components last agreed with; runtime only, never saved. */
internal class CanvasElementLink(var version: Int)

/** Every component type a canvas element is made of, the always-present ones first. */
internal val CANVAS_COMPONENT_TYPES: List<KClass<out Any>> = listOf(
    CanvasRect::class, CanvasPointerState::class, CanvasText::class, CanvasAppearance::class, CanvasLayout::class,
    CanvasCluster::class, CanvasBinding::class, CanvasFollow::class, CanvasFollowArrow::class, CanvasInput::class, CanvasTooltip::class,
)

/** Every widget component type, in the order a node with more than one keeps them: the first wins. */
internal val CANVAS_WIDGET_TYPES: List<KClass<out CanvasWidget>> = listOf(
    CanvasButtonWidget::class, CanvasTextFieldWidget::class, CanvasJoystickWidget::class, CanvasLookAreaWidget::class,
    CanvasStepperWidget::class, CanvasBarWidget::class, CanvasSweepWidget::class, CanvasViewWidget::class, CanvasImageWidget::class,
)

/**
 * Brings every canvas element of this world in step with its entity's components, as [SceneCanvas]
 * does before each frame: an entity given a [CanvasRect] gets its element, an element added whole,
 * as `world.add(entity, CanvasElement())` does, gets its components, and an entity whose
 * [CanvasRect] or [CanvasElement] was removed is no longer an element at all.
 *
 * Call it before reading canvas components that code changed through an element, or elements whose
 * entity's components code changed, in the same frame; a system that runs before the canvas draws,
 * as an action system does, calls it first.
 */
fun World.syncCanvasElements() {
    // Each pass changes only families other than the one it walks; removing an element would change
    // the links walked first, so those wait until the walk is done.
    var gone: MutableList<Entity>? = null
    family<CanvasElementLink>().forEach { entity, link ->
        if (!syncLinked(entity, link)) gone = (gone ?: ArrayList()).also { it += entity }
    }
    family<CanvasElement>().forEach { entity, element -> if (!has<CanvasElementLink>(entity)) adopt(entity, element, push = true) }
    family<CanvasRect>().forEach { entity, _ ->
        if (!has<CanvasElementLink>(entity) && !has<CanvasElement>(entity)) adopt(entity, emptyCanvasElement(), push = false)
    }
    gone?.forEach { removeCanvasElement(it) }
}

/**
 * Brings [entity]'s canvas element in step with its components, as [syncCanvasElements] does for
 * every element: what an export or a lookup of one entity calls first.
 */
fun World.syncCanvasElement(entity: Entity) {
    if (!isAlive(entity)) return
    val element = get<CanvasElement>(entity)
    val link = get<CanvasElementLink>(entity)
    when {
        element == null && link == null -> if (has<CanvasRect>(entity)) adopt(entity, emptyCanvasElement(), push = false)
        link == null -> adopt(entity, checkNotNull(element), push = true)
        !syncLinked(entity, link) -> removeCanvasElement(entity)
    }
}

/**
 * Pushes or pulls [entity]'s components by its [link], and returns false, changing nothing, when the
 * entity has lost its element or its [CanvasRect] and so is no longer a canvas element.
 */
private fun World.syncLinked(entity: Entity, link: CanvasElementLink): Boolean {
    val element = get<CanvasElement>(entity)
    val changedHere = element != null && element.componentsVersion != link.version
    if (element == null || !changedHere && !has<CanvasRect>(entity)) return false
    if (changedHere) push(entity, element) else pull(entity, element)
    link.version = element.componentsVersion
    return true
}

/**
 * Makes [entity] the canvas element [element]: the element and each of its components are added to
 * the entity, in place of any it had. What a host that builds elements in code calls in place of
 * `world.add(entity, element)`, which [syncCanvasElements] also takes, a frame later.
 */
fun World.addCanvasElement(entity: Entity, element: CanvasElement) {
    remove<CanvasElementLink>(entity)
    add(entity, element)
    adopt(entity, element, push = true)
}

/** Makes [entity] no canvas element: its element and every canvas component it has are removed. */
fun World.removeCanvasElement(entity: Entity) {
    remove<CanvasElement>(entity)
    remove<CanvasElementLink>(entity)
    for (type in CANVAS_COMPONENT_TYPES) remove(entity, type)
    for (type in CANVAS_WIDGET_TYPES) remove(entity, type)
}

/**
 * Gives [entity] [component], a canvas element component, both as one of its own components and as
 * its element's, making the element first when it has none: how a binding attaches what a scene file holds.
 */
internal fun World.attachCanvasComponent(entity: Entity, component: Any) {
    val element = get<CanvasElement>(entity) ?: emptyCanvasElement().also { addCanvasElement(entity, it) }
    val link = get<CanvasElementLink>(entity)
    val inStep = link != null && link.version == element.componentsVersion
    if (component is CanvasWidget) element.widget?.let { remove(entity, it::class) }
    element.set(component)
    @Suppress("UNCHECKED_CAST") // The component is an instance of its own class.
    add(entity, component::class as KClass<Any>, component)
    if (inStep) link.version = element.componentsVersion
}

/** A new element with no components but the [CanvasRect] and [CanvasPointerState] every element has: a Panel. */
internal fun emptyCanvasElement(): CanvasElement = CanvasElement().apply { remove(CanvasText::class) }

private fun World.adopt(entity: Entity, element: CanvasElement, push: Boolean) {
    if (get<CanvasElement>(entity) !== element) add(entity, element)
    if (push) push(entity, element) else pull(entity, element)
    add(entity, CanvasElementLink(element.componentsVersion))
}

/** Sets the entity's components to the element's: one it lacks is added, one it no longer has removed. */
@Suppress("UNCHECKED_CAST") // Each component is added under its own type.
private fun World.push(entity: Entity, element: CanvasElement) {
    for (type in CANVAS_COMPONENT_TYPES) {
        val mine = element.get(type)
        if (mine !== get(entity, type)) if (mine == null) remove(entity, type) else add(entity, type as KClass<Any>, mine)
    }
    val widget = element.widget
    for (type in CANVAS_WIDGET_TYPES) {
        val mine = widget?.takeIf { type.isInstance(it) }
        if (mine !== get(entity, type)) if (mine == null) remove(entity, type) else add(entity, type as KClass<Any>, mine)
    }
}

/** Sets the element's components to the entity's; the entity gets back a [CanvasRect] or [CanvasPointerState] it lost. */
@Suppress("UNCHECKED_CAST") // Each component is added under its own type.
private fun World.pull(entity: Entity, element: CanvasElement) {
    for (type in CANVAS_COMPONENT_TYPES) {
        val theirs = get(entity, type)
        val mine = element.get(type)
        when {
            theirs === mine -> Unit
            theirs != null -> element.set(theirs)
            type == CanvasRect::class || type == CanvasPointerState::class -> add(entity, type as KClass<Any>, checkNotNull(mine))
            else -> element.remove(type)
        }
    }
    val theirs = CANVAS_WIDGET_TYPES.firstNotNullOfOrNull { get(entity, it) }
    if (theirs !== element.widget) if (theirs == null) element.remove(CanvasWidget::class) else element.set(theirs)
}
