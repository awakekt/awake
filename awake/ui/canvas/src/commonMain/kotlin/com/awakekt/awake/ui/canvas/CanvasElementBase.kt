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
@Suppress("TooManyFunctions") // A small helper for each component the flat properties add.
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
    internal var particlesPart: CanvasParticles? = null

    /**
     * Bumped each time a component is set or removed through this element, so a host that keeps the
     * same components elsewhere, as a scene's entity does, can tell its own copy is out of date.
     */
    var componentsVersion: Int = 0
        internal set

    /**
     * The element's component of [type], or null when it has none; [CanvasRect] and [CanvasPointerState] always answer.
     *
     * It allocates nothing, so it can be read every frame; on the JVM, keep [type] in a `val` there, since each
     * `X::class` written out makes a new class reference.
     */
    @Suppress("UNCHECKED_CAST") // Each branch answers the field that holds exactly that type.
    fun <T : Any> get(type: KClass<T>): T? = when (type) {
        RECT_TYPE -> rect
        POINTER_TYPE -> pointer
        TEXT_TYPE -> textPart
        APPEARANCE_TYPE -> appearancePart
        LAYOUT_TYPE -> layout
        CLUSTER_TYPE -> cluster
        BINDING_TYPE -> bind
        FOLLOW_TYPE -> followPart
        ARROW_TYPE -> arrowPart
        INPUT_TYPE -> inputPart
        TOOLTIP_TYPE -> tooltipPart
        PARTICLES_TYPE -> particlesPart
        else -> widget?.takeIf { type.isInstance(it) }
    } as T?

    /**
     * Sets [component] as this element's component of its type, in place of the one it had: a
     * [CanvasWidget] takes the place of the element's widget, whichever it was.
     *
     * @throws IllegalArgumentException when [component] is not a canvas element component.
     */
    @Suppress("CyclomaticComplexMethod") // One branch for each component type.
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
            is CanvasParticles -> particles = component
            is CanvasWidget -> install(component, carries = false)
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
        require(type != RECT_TYPE && type != POINTER_TYPE) { "every canvas element has a ${type.simpleName}" }
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
            is CanvasParticles -> particles = null
            is CanvasWidget -> widget = null
        }
        componentsVersion++
        return removed
    }

    /** Every component the element has, [rect] and [pointer] first. */
    fun components(): List<Any> = listOfNotNull(
        rect, pointer, textPart, appearancePart, layout, cluster, bind, followPart, arrowPart, inputPart, tooltipPart, particles, widget,
    )

    /**
     * Makes [next] the element's widget. A widget the element made itself, for a new [kind] or a
     * flat property, [carries] the fill or action the one before it had; one set whole keeps its own.
     */
    internal fun install(next: CanvasWidget, carries: Boolean = true) {
        val previous = widget
        if (next is CanvasButtonWidget) next.clock = pointer.clock
        if (carries) carryShared(previous, next) else looseShared(previous)
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

    /** Particles emitted from or over this element; null when it has none. */
    var particles: CanvasParticles?
        get() = particlesPart
        set(value) {
            val previous = particlesPart
            if (previous !== value) {
                if (previous != null && previous.owner === this) previous.owner = null
                if (value != null && this is CanvasElement) value.owner = this
            }
            particlesPart = value
            componentsVersion++
        }
}

// The component types [CanvasElementBase.get] and [CanvasElementBase.remove] compare against, made
// once: on the JVM without kotlin-reflect each `X::class` makes a new class reference, and reads run
// every frame (#1169).
private val RECT_TYPE: KClass<CanvasRect> = CanvasRect::class
private val POINTER_TYPE: KClass<CanvasPointerState> = CanvasPointerState::class
private val TEXT_TYPE: KClass<CanvasText> = CanvasText::class
private val APPEARANCE_TYPE: KClass<CanvasAppearance> = CanvasAppearance::class
private val LAYOUT_TYPE: KClass<CanvasLayout> = CanvasLayout::class
private val CLUSTER_TYPE: KClass<CanvasCluster> = CanvasCluster::class
private val BINDING_TYPE: KClass<CanvasBinding> = CanvasBinding::class
private val FOLLOW_TYPE: KClass<CanvasFollow> = CanvasFollow::class
private val ARROW_TYPE: KClass<CanvasFollowArrow> = CanvasFollowArrow::class
private val INPUT_TYPE: KClass<CanvasInput> = CanvasInput::class
private val TOOLTIP_TYPE: KClass<CanvasTooltip> = CanvasTooltip::class
private val PARTICLES_TYPE: KClass<CanvasParticles> = CanvasParticles::class

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
