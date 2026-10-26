/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

/**
 * The words a canvas element draws: a Text's content, a Button's label, what is typed in a
 * TextField, and the font any of them draws in. An element with words but no widget component is a
 * Text. Its colour is the element's [CanvasAppearance.color].
 */
class CanvasText {
    /** The words shown while [textKey] is empty: a Text's content, a Button's label, a TextField's typed line. */
    var text: String = ""

    /**
     * The key of the words in the project's string tables, such as `hud.greeting`; empty shows
     * [text]. While it is set, the words are the key's text in the active locale of the `Strings`
     * [CanvasOverlay] was given, with [textArgs] put into its placeholders, and they change when the
     * locale does. A key no table has shows as the key and is logged once.
     */
    var textKey: String = ""

    /**
     * Values for [textKey]'s `{name}`, `plural` and `select` placeholders, and for a TextField's
     * placeholder hint, by name, such as `count` to `3`; a number placeholder reads a value written
     * as a number. A field a [CanvasBinding] reads into an argument, through
     * [CanvasBinding.textArgs], takes the place of the value of the same name here.
     */
    var textArgs: Map<String, String> = emptyMap()

    /** Font size in scale-independent pixels. */
    var fontSize: Float = DEFAULT_FONT_SIZE

    /**
     * The font the words draw in: a project `.ttf` or `.otf` file that [CanvasOverlay] was given, or
     * a built-in family, `default` or `bitmap`. Empty, a file that did not load, or a name that is
     * neither draws in the default font.
     */
    var font: String = ""

    /** Where the words sit in the element; null keeps the widget's own, top-left or centred. */
    var textAlign: CanvasAnchor? = null

    /**
     * Whether `[[key|shown words]]` spans draw as links.
     *
     * False for text a game cannot trust, such as player chat: `[[` and `]]` then draw as typed and
     * never report a key, so a player cannot make their own line clickable.
     */
    var links: Boolean = true

    /** Default text metrics. */
    companion object {
        /** Default font size in sp. */
        const val DEFAULT_FONT_SIZE = 18f
    }
}
