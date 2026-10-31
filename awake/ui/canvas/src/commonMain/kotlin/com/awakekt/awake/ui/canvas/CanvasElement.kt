/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.math2d.Vec2

/**
 * A screen-space UI element, drawn over the game by [CanvasOverlay]: the components that make it
 * up, held together. A host places it on whatever it uses to hold game objects; `awake:scene:canvas`
 * keeps one for each canvas entity, made of that entity's components.
 *
 * Every element has a [rect], where it sits, and a [pointer], what the player is doing to it, which
 * is never saved. The rest are optional, one of each type, set with [set] and read with [get]:
 * - [CanvasText], its words; [CanvasAppearance], its colours, style and skin;
 * - [CanvasLayout] or [CanvasCluster], how it places its children; [CanvasBinding], game state it shows;
 * - [CanvasFollow] or [CanvasFollowArrow], world following; [CanvasInput] and [CanvasTooltip];
 * - one [CanvasWidget], such as a [CanvasButtonWidget] or a [CanvasBarWidget], that makes it a control.
 *
 * Its [kind] follows from them: its widget's, else [CanvasElementKind.Text] when it has words, else
 * [CanvasElementKind.Panel]. A new element is an empty Text.
 *
 * The flat properties below, such as [width], [text] or [value], read and write those components
 * and stay for code written before the split. Each reads its default while its component is absent;
 * writing a value other than the default adds the component, unless it belongs to another widget
 * than the element's, which ignores it, as an option on the wrong kind always did.
 */
@Suppress("TooManyFunctions") // One small reader per input an element reports, kept for older code.
class CanvasElement : CanvasElementWidgetFields() {
    /** See [CanvasPointerState.consumePress]: true once for each tap since the last call. */
    fun consumePress(): Boolean = pointer.consumePress()

    /** See [CanvasPointerState.consumePressAt]. */
    fun consumePressAt(): Vec2? = pointer.consumePressAt()

    /** See [CanvasPointerState.presses]. */
    val presses: Int get() = pointer.presses

    /** See [CanvasPointerState.consumeDoublePress]. */
    fun consumeDoublePress(): Boolean = pointer.consumeDoublePress()

    /** See [CanvasPointerState.secondaryPressed]. */
    val secondaryPressed: Boolean get() = pointer.secondaryPressed

    /** See [CanvasPointerState.consumeSecondaryPress]. */
    fun consumeSecondaryPress(): Boolean = pointer.consumeSecondaryPress()

    /** See [CanvasPointerState.isHeld]. */
    val isHeld: Boolean get() = pointer.isHeld

    /** See [CanvasPointerState.isHovered]. */
    val isHovered: Boolean get() = pointer.isHovered

    /** See [CanvasPointerState.consumeWheel]. */
    fun consumeWheel(): Float = pointer.consumeWheel()

    /** See [CanvasPointerState.dragging]. */
    var dragging: Boolean
        get() = pointer.dragging
        internal set(value) {
            pointer.dragging = value
        }

    /** See [CanvasPointerState.dropped]. */
    val dropped: CanvasElement? get() = pointer.dropped

    /** See [CanvasPointerState.droppedOutside]. */
    val droppedOutside: Vec2? get() = pointer.droppedOutside

    /** See [CanvasPointerState.moving]. */
    val moving: Boolean get() = pointer.moving

    /** See [CanvasPointerState.moved]. */
    val moved: Vec2? get() = pointer.moved

    /**
     * Draws this root element over every other root of the same [order], and gives it the pointer
     * first, as pressing in a window with a [moveHandle] does. On an element inside another it does
     * nothing; it is never saved.
     */
    fun bringToFront() = raiseCanvasElement(this)

    /** See [CanvasPointerState.focused]. */
    var focused: Boolean
        get() = pointer.focused
        set(value) {
            pointer.focused = value
        }

    /** See [CanvasPointerState.consumeSubmit]. */
    fun consumeSubmit(): String? = pointer.consumeSubmit()

    /** See [CanvasPointerState.stickX]. */
    val stickX: Float get() = pointer.stickX

    /** See [CanvasPointerState.stickY]. */
    val stickY: Float get() = pointer.stickY

    /** See [CanvasPointerState.sprinting]. */
    val sprinting: Boolean get() = pointer.sprinting

    /** See [CanvasPointerState.consumeSprintStart]. */
    fun consumeSprintStart(): Boolean = pointer.consumeSprintStart()

    /** See [CanvasPointerState.consumeLook]. */
    fun consumeLook(): Vec2 = pointer.consumeLook()

    /** See [CanvasPointerState.consumeZoom]. */
    fun consumeZoom(): Float = pointer.consumeZoom()

    /** See [CanvasPointerState.consumeDown]. */
    fun consumeDown(): Boolean = pointer.consumeDown()

    /** See [CanvasPointerState.consumeRelease]. */
    fun consumeRelease(): Boolean = pointer.consumeRelease()

    /** See [CanvasPointerState.consumeLongPress]. */
    fun consumeLongPress(): Boolean = pointer.consumeLongPress()

    /** Whether an action button with [CanvasButton.aim] is held and being dragged to aim right now. */
    val aiming: Boolean get() = pointer.buttonPress.aiming(this)

    /** See [CanvasPointerState.aimX]. */
    val aimX: Float get() = pointer.aimX

    /** See [CanvasPointerState.aimY]. */
    val aimY: Float get() = pointer.aimY

    /** See [CanvasPointerState.aimCancelling]. */
    val aimCancelling: Boolean get() = pointer.aimCancelling

    /** See [CanvasPointerState.aimTarget]. */
    val aimTarget: String? get() = pointer.aimTarget

    /** See [CanvasPointerState.consumeAimCancel]. */
    fun consumeAimCancel(): Boolean = pointer.consumeAimCancel()

    /** See [CanvasPointerState.heldSeconds]. */
    val heldSeconds: Float get() = pointer.heldSeconds

    /** Default dimensions and styling metrics for canvas elements. */
    companion object {
        /** Default edge inset offset in dp. */
        const val DEFAULT_INSET = CanvasRect.DEFAULT_INSET

        /** Default element width in dp. */
        const val DEFAULT_WIDTH = CanvasRect.DEFAULT_WIDTH

        /** Default element height in dp. */
        const val DEFAULT_HEIGHT = CanvasRect.DEFAULT_HEIGHT

        /** Default font size in sp. */
        const val DEFAULT_FONT_SIZE = CanvasText.DEFAULT_FONT_SIZE

        /** How soon a second press makes a double-click, as the common desktop default. */
        const val DOUBLE_CLICK_SECONDS = 0.5

        /** How long the pointer is over an element with a [tooltip] before the hint shows. */
        const val TOOLTIP_DELAY_SECONDS = 0.5f
    }
}

/**
 * The element's component of type [T], or null when it has none; see [CanvasElement.get]. On the JVM
 * each call makes a new class reference for [T], so a read every frame passes a type kept in a `val`
 * to [CanvasElement.get] instead.
 */
inline fun <reified T : Any> CanvasElement.get(): T? = get(T::class)

/**
 * The key of a `[[key|shown words]]` span clicked in [CanvasElement.text] since the last call, then
 * null.
 *
 * @return The clicked span's key, or null when none was clicked since the last call.
 */
fun CanvasElement.consumeLink(): String? = pointer.consumeLink()
