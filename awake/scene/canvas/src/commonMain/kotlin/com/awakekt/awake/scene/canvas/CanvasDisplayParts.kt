/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasBar
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasImage
import com.awakekt.awake.ui.canvas.CanvasSweep

// The handles of the element kinds that show something: words, a box, a picture, a camera's view
// and a fill. The controls a player works are in CanvasControlParts.kt.

/** A [CanvasElementKind.Text] element: words, typed in or from a string table. */
class CanvasTextPart internal constructor(id: String) : CanvasPart(id, CanvasElementKind.Text) {
    /** The words shown while [textKey] is empty; see [CanvasElement.text]. */
    var text: String
        get() = element.text
        set(value) {
            element.text = value
        }

    /** The key of the words in the project's string tables; see [CanvasElement.textKey]. */
    var textKey: String
        get() = element.textKey
        set(value) {
            element.textKey = value
        }

    /** The size of the words, in sp. */
    var fontSize: Float
        get() = element.fontSize
        set(value) {
            element.fontSize = value
        }

    /** The font the words draw in; see [CanvasElement.font]. */
    var font: String
        get() = element.font
        set(value) {
            element.font = value
        }

    /** Where the words sit in the element; null keeps the kind's own. */
    var textAlign: CanvasAnchor?
        get() = element.textAlign
        set(value) {
            element.textAlign = value
        }

    /** Whether `[[key|words]]` spans draw as links; see [CanvasElement.links]. */
    var links: Boolean
        get() = element.links
        set(value) {
            element.links = value
        }
}

/** A [CanvasElementKind.Panel] element: a box that holds others, pressable when asked. */
class CanvasPanelPart internal constructor(id: String) : CanvasPart(id, CanvasElementKind.Panel) {
    /** Whether the panel takes a tap as a Button does; see [CanvasElement.pressable]. */
    var pressable: Boolean
        get() = element.pressable
        set(value) {
            element.pressable = value
        }

    /** True once for each tap on a [pressable] panel since the last call; see [CanvasElement.consumePress]. */
    fun consumePress(): Boolean = element.consumePress()
}

/** A [CanvasElementKind.Image] element: a picture, its style's image. */
class CanvasImagePart internal constructor(id: String) : CanvasPart(id, CanvasElementKind.Image) {
    /**
     * The picture shown, its style's [image][com.awakekt.awake.ui.canvas.CanvasStyle.image]. Writing
     * the picture already shown changes nothing, so a binder can write it every frame.
     */
    var image: CanvasImage?
        get() = element.style.image
        set(value) {
            val style = element.style
            if (style.image != value) element.style = style.copy(image = value)
        }

    /** Whether the picture takes a tap as a Button does; see [CanvasElement.pressable]. */
    var pressable: Boolean
        get() = element.pressable
        set(value) {
            element.pressable = value
        }

    /** True once for each tap on a [pressable] picture since the last call; see [CanvasElement.consumePress]. */
    fun consumePress(): Boolean = element.consumePress()
}

/** A [CanvasElementKind.View] element: what a camera sees. */
class CanvasViewPart internal constructor(id: String) : CanvasPart(id, CanvasElementKind.View) {
    /** The name of the node whose camera the view shows; see [CanvasElement.camera]. */
    var camera: String
        get() = element.camera
        set(value) {
            element.camera = value
        }
}

/** A [CanvasElementKind.Bar] element: a progress bar. */
class CanvasBarPart internal constructor(id: String) : CanvasPart(id, CanvasElementKind.Bar) {
    /** The fill, from 0 to 1; see [CanvasElement.value]. */
    var value: Float
        get() = element.value
        set(value) {
            element.value = value
        }

    /** How the bar draws: direction, track, trail, segments, ticks and label. */
    var bar: CanvasBar?
        get() = element.bar
        set(value) {
            element.bar = value
        }
}

/** A [CanvasElementKind.Sweep] element: a radial fill, such as a cooldown. */
class CanvasSweepPart internal constructor(id: String) : CanvasPart(id, CanvasElementKind.Sweep) {
    /** The share of a full turn filled, from 0 to 1; see [CanvasElement.value]. */
    var value: Float
        get() = element.value
        set(value) {
            element.value = value
        }

    /** How the sweep fills its share of a turn. */
    var sweep: CanvasSweep?
        get() = element.sweep
        set(value) {
            element.sweep = value
        }
}
