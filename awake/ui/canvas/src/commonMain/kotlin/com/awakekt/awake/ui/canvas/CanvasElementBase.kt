/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlin.reflect.KClass

/**
 * What every [CanvasElement] is made of: its components, held one of each type, and the [kind] they
 * make it. [CanvasElement] is the only kind of it; the flat properties kept for code written before
 * the split are in [CanvasElementFields], [CanvasElementFollowFields] and
 * [CanvasElementWidgetFields], split only to keep each file short.
 */
sealed class CanvasElementBase {
    /** Where the element sits and how big it is. */
    var rect: CanvasRect = CanvasRect()
        private set

    /** What the player is doing to the element right now; runtime only, never saved. */
    var pointer: CanvasPointerState = CanvasPointerState()
        private set

    /** The control the element is, or null for a Text or a Panel. */
    var widget: CanvasWidget? = null
        internal set

    internal var textPart: CanvasText? = CanvasText()
    internal var appearancePart: CanvasAppearance? = null
    internal var followPart: CanvasFollow? = null
    internal var arrowPart: CanvasFollowArrow? = null
    internal var inputPart: CanvasInput? = null
    internal var tooltipPart: CanvasTooltip? = null

    /**
     * Bumped each time a component is set or removed through this element, so a host that keeps the
     * same components elsewhere, as a scene's entity does, can tell its own copy is out of date.
     */
    var componentsVersion: Int = 0
        internal set

    /** The element's component of [type], or null when it has none; [CanvasRect] and [CanvasPointerState] always answer. */
    @Suppress("UNCHECKED_CAST") // Each branch answers the field that holds exactly that type.
    fun <T : Any> get(type: KClass<T>): T? = when (type) {
        CanvasRect::class -> rect
        CanvasPointerState::class -> pointer
        CanvasText::class -> textPart
        CanvasAppearance::class -> appearancePart
        CanvasLayout::class -> layout
        CanvasCluster::class -> cluster
        CanvasBinding::class -> bind
        CanvasFollow::class -> followPart
        CanvasFollowArrow::class -> arrowPart
        CanvasInput::class -> inputPart
        CanvasTooltip::class -> tooltipPart
        else -> widget?.takeIf { type.isInstance(it) }
    } as T?

    /**
     * Sets [component] as this element's component of its type, in place of the one it had: a
     * [CanvasWidget] takes the place of the element's widget, whichever it was.
     *
     * @throws IllegalArgumentException when [component] is not a canvas element component.
     */
    fun set(component: Any) {
        when (component) {
            is CanvasRect -> rect = component
            is CanvasPointerState -> pointer = component.also { it.clock = pointer.clock }
            is CanvasText -> textPart = component
            is CanvasAppearance -> appearancePart = component
            is CanvasLayout -> layout = component
            is CanvasCluster -> cluster = component
            is CanvasBinding -> bind = component
            is CanvasFollow -> followPart = component
            is CanvasFollowArrow -> arrowPart = component
            is CanvasInput -> inputPart = component
            is CanvasTooltip -> tooltipPart = component
            is CanvasWidget -> install(component)
            else -> throw IllegalArgumentException("${component::class.simpleName} is not a canvas element component")
        }
        componentsVersion++
    }

    /**
     * Removes the element's component of [type], when it has one, and returns it.
     *
     * @throws IllegalArgumentException for [CanvasRect] and [CanvasPointerState], which every element has.
     */
    fun <T : Any> remove(type: KClass<T>): T? {
        require(type != CanvasRect::class && type != CanvasPointerState::class) { "every canvas element has a ${type.simpleName}" }
        val removed = get(type) ?: return null
        when (removed) {
            is CanvasText -> textPart = null
            is CanvasAppearance -> appearancePart = null
            is CanvasLayout -> layout = null
            is CanvasCluster -> cluster = null
            is CanvasBinding -> bind = null
            is CanvasFollow -> followPart = null
            is CanvasFollowArrow -> arrowPart = null
            is CanvasInput -> inputPart = null
            is CanvasTooltip -> tooltipPart = null
            is CanvasWidget -> widget = null
        }
        componentsVersion++
        return removed
    }

    /** Every component the element has, [rect] and [pointer] first. */
    fun components(): List<Any> = listOfNotNull(
        rect, pointer, textPart, appearancePart, layout, cluster, bind, followPart, arrowPart, inputPart, tooltipPart, widget,
    )

    internal fun install(next: CanvasWidget) {
        val previous = widget
        if (next is CanvasButtonWidget) next.clock = pointer.clock
        carryShared(previous, next)
        widget = next
    }

    /**
     * What the element draws, as its components say. Setting it gives the element that kind's
     * widget, in place of the one it had, keeping the fill or action the two share; a Text gets
     * words when it has none, and a Panel gives up its words.
     */
    var kind: CanvasElementKind
        get() = widget?.kind ?: if (textPart != null) CanvasElementKind.Text else CanvasElementKind.Panel
        set(value) {
            if (value == kind) return
            when (value) {
                CanvasElementKind.Text -> if (textPart == null) textPart = CanvasText()
                CanvasElementKind.Panel -> textPart = null
                else -> Unit
            }
            val next = widgetOf(value)
            if (next == null) {
                looseShared(widget)
                widget = null
            } else {
                install(next)
            }
            componentsVersion++
        }

    // Fields two widgets share, held while the element has neither, so either can take them.
    internal var looseValue = 1f
    internal var looseAction = ""

    internal fun looseShared(previous: CanvasWidget?) {
        previous?.sharedValue?.let { looseValue = it }
        previous?.sharedAction?.let { looseAction = it }
    }

    internal fun carryShared(previous: CanvasWidget?, next: CanvasWidget) {
        looseShared(previous)
        when (next) {
            is CanvasBarWidget -> next.value = looseValue
            is CanvasSweepWidget -> next.value = looseValue
            is CanvasButtonWidget -> next.action = looseAction
            is CanvasJoystickWidget -> next.action = looseAction
            is CanvasLookAreaWidget -> next.action = looseAction
            else -> Unit
        }
    }

    /** This element's widget of type [W]; else, when [adds] and the element has no widget, a new one from [make]. */
    internal inline fun <reified W : CanvasWidget> widgetFor(adds: Boolean, make: () -> W): W? =
        widget as? W ?: if (adds && widget == null) make().also { install(it); componentsVersion++ } else null

    internal fun textFor(adds: Boolean): CanvasText? =
        textPart ?: if (adds && kind != CanvasElementKind.Panel) CanvasText().also { set(it) } else null

    internal fun lookFor(adds: Boolean): CanvasAppearance? = appearancePart ?: if (adds) CanvasAppearance().also { set(it) } else null

    internal fun followFor(adds: Boolean): CanvasFollow? = followPart ?: if (adds) CanvasFollow().also { set(it) } else null

    internal fun inputFor(adds: Boolean): CanvasInput? = inputPart ?: if (adds) CanvasInput().also { set(it) } else null

    /** How the element places its children itself, in rows or columns; null anchors each child by hand. */
    var layout: CanvasLayout? = null
        set(value) {
            field = value
            componentsVersion++
        }

    /** Places the element's children as a button cluster, in place of [layout]; null places them as [layout] says. */
    var cluster: CanvasCluster? = null
        set(value) {
            field = value
            componentsVersion++
        }

    /** Game state the element shows without code; while it reads, it stands in for [value], [text] and [textArgs]. */
    var bind: CanvasBinding? = null
        set(value) {
            field = value
            componentsVersion++
        }
}

/** A new widget of [kind], or null for the kinds that have none. */
internal fun widgetOf(kind: CanvasElementKind): CanvasWidget? = when (kind) {
    CanvasElementKind.Text, CanvasElementKind.Panel -> null
    CanvasElementKind.Bar -> CanvasBarWidget()
    CanvasElementKind.Button -> CanvasButtonWidget()
    CanvasElementKind.TextField -> CanvasTextFieldWidget()
    CanvasElementKind.Joystick -> CanvasJoystickWidget()
    CanvasElementKind.Image -> CanvasImageWidget()
    CanvasElementKind.View -> CanvasViewWidget()
    CanvasElementKind.LookArea -> CanvasLookAreaWidget()
    CanvasElementKind.Sweep -> CanvasSweepWidget()
    CanvasElementKind.Stepper -> CanvasStepperWidget()
}

/** The fill two widgets share, or null for a widget with none. */
internal val CanvasWidget.sharedValue: Float?
    get() = when (this) {
        is CanvasBarWidget -> value
        is CanvasSweepWidget -> value
        else -> null
    }

/** The action three widgets share, or null for a widget with none. */
internal val CanvasWidget.sharedAction: String?
    get() = when (this) {
        is CanvasButtonWidget -> action
        is CanvasJoystickWidget -> action
        is CanvasLookAreaWidget -> action
        else -> null
    }
