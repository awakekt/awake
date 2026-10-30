/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.math.Vec3f

/**
 * The flat properties of a [CanvasElement] that read and write how it follows a node and takes the
 * pointer: its [CanvasFollow], [CanvasFollowArrow], [CanvasInput] and [CanvasTooltip]. Each reads
 * its default while its component is absent, and writing a value other than the default adds it.
 */
sealed class CanvasElementFollowFields : CanvasElementFields() {
    /** See [CanvasFollow.node]. */
    var follow: String
        get() = followPart?.node ?: ""
        set(value) {
            followFor(value.isNotEmpty())?.node = value
        }

    /** See [CanvasFollow.offset]. */
    var followOffset: Vec3f
        get() = followPart?.offset ?: Vec3f(0f, 0f, 0f)
        set(value) {
            followFor(value != Vec3f(0f, 0f, 0f))?.offset = value
        }

    /** See [CanvasFollow.bounds]. */
    var followBounds: Boolean
        get() = followPart?.bounds ?: false
        set(value) {
            followFor(value)?.bounds = value
        }

    /** See [CanvasFollow.outset]. */
    var followOutset: Float
        get() = followPart?.outset ?: 0f
        set(value) {
            followFor(value != 0f)?.outset = value
        }

    /** See [CanvasFollow.rules]. */
    var followRules: CanvasFollowRules?
        get() = followPart?.rules
        set(value) {
            followFor(value != null)?.rules = value
        }

    /** See [CanvasFollow.score]. */
    var followScore: Float
        get() = followPart?.score ?: 0f
        set(value) {
            followFor(value != 0f)?.score = value
        }

    /** See [CanvasFollow.pinned]. */
    var pinned: Boolean
        get() = followPart?.pinned ?: false
        internal set(value) {
            followPart?.pinned = value
        }

    /** See [CanvasFollow.pinAngle]. */
    var pinAngle: Float
        get() = followPart?.pinAngle ?: 0f
        internal set(value) {
            followPart?.pinAngle = value
        }

    /** See [CanvasFollow.distance]. */
    var followDistance: Float
        get() = followPart?.distance ?: 0f
        internal set(value) {
            followPart?.distance = value
        }

    /** Whether the element has a [CanvasFollowArrow]: the arrow of a follower held to the screen's edge. */
    var followArrow: Boolean
        get() = arrowPart != null
        set(value) {
            if (value && arrowPart == null) {
                set(CanvasFollowArrow())
            } else if (!value && arrowPart != null) {
                arrowPart = null
                componentsVersion++
            }
        }

    internal var arrowAngle: Float
        get() = arrowPart?.angle ?: 0f
        set(value) {
            arrowPart?.angle = value
        }

    /** See [CanvasInput.pressable]. */
    var pressable: Boolean
        get() = inputPart?.pressable ?: false
        set(value) {
            inputFor(value)?.pressable = value
        }

    /** See [CanvasInput.draggable]. */
    var draggable: Boolean
        get() = inputPart?.draggable ?: false
        set(value) {
            inputFor(value)?.draggable = value
        }

    /** See [CanvasInput.dropTarget]. */
    var dropTarget: Boolean
        get() = inputPart?.dropTarget ?: false
        set(value) {
            inputFor(value)?.dropTarget = value
        }

    /** See [CanvasInput.wheel]. */
    var wheel: Boolean
        get() = inputPart?.wheel ?: false
        set(value) {
            inputFor(value)?.wheel = value
        }

    /** See [CanvasTooltip.text]. */
    var tooltip: String
        get() = tooltipPart?.text ?: ""
        set(value) {
            val hint = tooltipPart ?: if (value.isNotEmpty()) CanvasTooltip().also { it.textArgs = textArgs; set(it) } else null
            hint?.text = value
        }
}
