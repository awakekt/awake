/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.math2d.Rectangle

/**
 * The flat properties of a [CanvasElement] that read and write its placement, words and look: its
 * [CanvasRect], [CanvasText] and [CanvasAppearance]. Each reads its default while its component is
 * absent, and writing a value other than the default adds the component; words are never added to a
 * Panel, which would make it a Text.
 */
sealed class CanvasElementFields : CanvasElementBase() {
    /** See [CanvasRect.anchor]. */
    var anchor: CanvasAnchor
        get() = rect.anchor
        set(value) {
            rect.anchor = value
        }

    /** See [CanvasRect.offsetX]. */
    var offsetX: Float
        get() = rect.offsetX
        set(value) {
            rect.offsetX = value
        }

    /** See [CanvasRect.offsetY]. */
    var offsetY: Float
        get() = rect.offsetY
        set(value) {
            rect.offsetY = value
        }

    /** See [CanvasRect.width]. */
    var width: Float
        get() = rect.width
        set(value) {
            rect.width = value
        }

    /** See [CanvasRect.height]. */
    var height: Float
        get() = rect.height
        set(value) {
            rect.height = value
        }

    /** See [CanvasRect.order]. */
    var order: Int
        get() = rect.order
        set(value) {
            rect.order = value
        }

    /** See [CanvasRect.visible]. */
    var visible: Boolean
        get() = rect.visible
        set(value) {
            rect.visible = value
        }

    /** See [CanvasRect.touchOnly]. */
    var touchOnly: Boolean
        get() = rect.touchOnly
        set(value) {
            rect.touchOnly = value
        }

    /** See [CanvasRect.safeArea]. */
    var safeArea: Boolean
        get() = rect.safeArea
        set(value) {
            rect.safeArea = value
        }

    /** See [CanvasRect.grow]. */
    var grow: Float
        get() = rect.grow
        set(value) {
            rect.grow = value
        }

    /** See [CanvasRect.elementId]. */
    var elementId: String
        get() = rect.elementId
        set(value) {
            rect.elementId = value
        }

    /** See [CanvasRect.controlId]. */
    var controlId: String
        get() = rect.controlId
        set(value) {
            rect.controlId = value
        }

    /** See [CanvasRect.screenBounds]. */
    var screenBounds: Rectangle?
        get() = rect.screenBounds
        internal set(value) {
            rect.screenBounds = value
        }

    internal var placedBounds: Rectangle?
        get() = rect.placedBounds
        set(value) {
            rect.placedBounds = value
        }

    /** See [CanvasText.text]: a Text's content, a Button's label, a TextField's typed line. */
    var text: String
        get() = textPart?.text ?: ""
        set(value) {
            textFor(value.isNotEmpty())?.text = value
        }

    /** See [CanvasText.textKey]. */
    var textKey: String
        get() = textPart?.textKey ?: ""
        set(value) {
            textFor(value.isNotEmpty())?.textKey = value
        }

    /**
     * See [CanvasText.textArgs]; on an element without words, its [CanvasTooltip.textArgs]. Setting
     * it sets both the element has.
     */
    var textArgs: Map<String, String>
        get() = textPart?.textArgs ?: tooltipPart?.textArgs ?: emptyMap()
        set(value) {
            val words = textFor(value.isNotEmpty() && tooltipPart == null)
            words?.textArgs = value
            val hint = tooltipPart ?: if (words == null && value.isNotEmpty()) CanvasTooltip().also { set(it) } else null
            hint?.textArgs = value
        }

    /** See [CanvasText.fontSize]. */
    var fontSize: Float
        get() = textPart?.fontSize ?: CanvasText.DEFAULT_FONT_SIZE
        set(value) {
            textFor(value != CanvasText.DEFAULT_FONT_SIZE)?.fontSize = value
        }

    /** See [CanvasText.font]. */
    var font: String
        get() = textPart?.font ?: ""
        set(value) {
            textFor(value.isNotEmpty())?.font = value
        }

    /** See [CanvasText.textAlign]. */
    var textAlign: CanvasAnchor?
        get() = textPart?.textAlign
        set(value) {
            textFor(value != null)?.textAlign = value
        }

    /** See [CanvasText.links]. */
    var links: Boolean
        get() = textPart?.links ?: true
        set(value) {
            textFor(!value)?.links = value
        }

    /** See [CanvasAppearance.color]. */
    var color: String
        get() = appearancePart?.color ?: CanvasAppearance.DEFAULT_COLOR
        set(value) {
            lookFor(value != CanvasAppearance.DEFAULT_COLOR)?.color = value
        }

    /** See [CanvasAppearance.background]. */
    var background: String
        get() = appearancePart?.background ?: CanvasAppearance.DEFAULT_BACKGROUND
        set(value) {
            lookFor(value != CanvasAppearance.DEFAULT_BACKGROUND)?.background = value
        }

    /** See [CanvasAppearance.style]. */
    var style: CanvasStyle
        get() = appearancePart?.style ?: DEFAULT_STYLE
        set(value) {
            lookFor(value != DEFAULT_STYLE)?.style = value
        }

    /** See [CanvasAppearance.skin]. */
    var skin: CanvasControlSkin?
        get() = appearancePart?.skin
        set(value) {
            lookFor(value != null)?.skin = value
        }

    /** See [CanvasAppearance.previewState]. */
    var previewState: CanvasControlState?
        get() = appearancePart?.previewState
        set(value) {
            lookFor(value != null)?.previewState = value
        }
}

private val DEFAULT_STYLE = CanvasStyle()
