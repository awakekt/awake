/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import kotlinx.serialization.Serializable

/** What a [CanvasElement] draws. */
@Serializable
enum class CanvasElementKind { Text, Panel, Bar, Button }

/** The screen point a [CanvasElement] is pinned to; its offset moves it inward from there. */
@Serializable
enum class CanvasAnchor {
    TopLeft, TopCenter, TopRight,
    CenterLeft, Center, CenterRight,
    BottomLeft, BottomCenter, BottomRight,
}

/**
 * A screen-space UI element on a scene entity, drawn over the game by [SceneCanvas].
 *
 * Sizes and offsets are in dp. Colours are `#RRGGBB` or `#RRGGBBAA`. [text] is a Text element's
 * content and a Button's label; [value] is a Bar's fill from 0 to 1.
 */
class CanvasElement {
    var kind: CanvasElementKind = CanvasElementKind.Text
    var anchor: CanvasAnchor = CanvasAnchor.TopLeft
    var offsetX: Float = DEFAULT_INSET
    var offsetY: Float = DEFAULT_INSET
    var width: Float = DEFAULT_WIDTH
    var height: Float = DEFAULT_HEIGHT
    var text: String = ""
    var fontSize: Float = DEFAULT_FONT_SIZE
    var color: String = "#FFFFFF"
    var background: String = "#00000000"
    var value: Float = 1f
    var order: Int = 0
    var visible: Boolean = true

    private var pressed = false

    /** True once for each tap on a Button since the last call; game code polls it. */
    fun consumePress(): Boolean = pressed.also { pressed = false }

    internal fun press() {
        pressed = true
    }

    companion object {
        const val DEFAULT_INSET = 16f
        const val DEFAULT_WIDTH = 200f
        const val DEFAULT_HEIGHT = 40f
        const val DEFAULT_FONT_SIZE = 18f
    }
}
