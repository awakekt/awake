/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind

/**
 * A typed handle to one element of a canvas prefab: the [id] a prefab saves it under, which
 * [canvasPrefab]'s builder writes when it is given the handle, and, once [resolve]d against a placed
 * copy, the live [CanvasElement] code writes through.
 *
 * Each element kind has its own handle, such as [CanvasImagePart] or [CanvasBarPart], that exposes
 * only the fields that kind uses; the fields every kind has are here. A handle comes from a
 * [CanvasParts] declaration, which composes its id, and is resolved with the rest of it, once, when
 * the prefab is placed. Reading or writing a field then touches the cached element only: no lookup
 * and no string is built, so a binder can write every frame.
 *
 * @property id The element's [CanvasElement.elementId], such as `bar.slot-3.icon`.
 * @property kind The element kind the handle stands for; [resolve] fails on an element of another.
 */
sealed class CanvasPart(val id: String, val kind: CanvasElementKind) {
    private var bound: CanvasElement? = null
    private var boundEntity: Long = 0L

    /** Whether the handle has been [resolve]d to an element of a placed prefab. */
    val isResolved: Boolean get() = bound != null

    /**
     * The live element the handle was resolved to, for a field no typed view exposes.
     *
     * @throws IllegalStateException when the handle has not been resolved.
     */
    val element: CanvasElement
        get() = bound ?: error(notResolved())

    /**
     * The entity the handle was resolved to.
     *
     * @throws IllegalStateException when the handle has not been resolved.
     */
    val entity: Entity
        get() {
            check(bound != null) { notResolved() }
            return Entity(boundEntity)
        }

    /** Whether the element is drawn; see [CanvasElement.visible]. */
    var visible: Boolean
        get() = element.visible
        set(value) {
            element.visible = value
        }

    /** How far the element moves from its anchor along the X axis, in dp; see [CanvasElement.offsetX]. */
    var offsetX: Float
        get() = element.offsetX
        set(value) {
            element.offsetX = value
        }

    /** How far the element moves from its anchor along the Y axis, in dp; see [CanvasElement.offsetY]. */
    var offsetY: Float
        get() = element.offsetY
        set(value) {
            element.offsetY = value
        }

    /** The element's width in dp. */
    var width: Float
        get() = element.width
        set(value) {
            element.width = value
        }

    /** The element's height in dp. */
    var height: Float
        get() = element.height
        set(value) {
            element.height = value
        }

    /** The colour of the element's words, fill or knob, as hex; see [CanvasElement.color]. */
    var color: String
        get() = element.color
        set(value) {
            element.color = value
        }

    /** The colour behind the element, as hex; see [CanvasElement.background]. */
    var background: String
        get() = element.background
        set(value) {
            element.background = value
        }

    /** The element's draw order among the elements beside it; see [CanvasElement.order]. */
    var order: Int
        get() = element.order
        set(value) {
            element.order = value
        }

    /** The key of the hint shown near the pointer after it rests on the element; see [CanvasElement.tooltip]. */
    var tooltip: String
        get() = element.tooltip
        set(value) {
            element.tooltip = value
        }

    /**
     * Values for the placeholders of the element's text keys, by name; see [CanvasElement.textArgs].
     * Writing the same map again changes nothing.
     */
    var textArgs: Map<String, String>
        get() = element.textArgs
        set(value) {
            if (element.textArgs != value) element.textArgs = value
        }

    internal fun bind(entity: Entity, element: CanvasElement) {
        boundEntity = entity.packed
        bound = element
    }

    private fun notResolved(): String = "canvas part \"$id\" is not resolved; resolve its CanvasParts against a placed root first"

    /** The handle's [id] and kind. */
    override fun toString(): String = "${kind.name} \"$id\""
}

/**
 * Thrown by [resolve] when a declared part is not an element of the placed prefab: no element at or
 * under the root has its id, as after a typo or a part renamed on one side only, or the element
 * that has it is of another kind.
 *
 * @property ids The ids of every part that did not resolve, in declaration order.
 */
class CanvasPartResolutionException internal constructor(
    message: String,
    val ids: List<String>,
) : IllegalStateException(message)
