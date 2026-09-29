/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/** What a [CanvasElement] draws. */
@Serializable
enum class CanvasElementKind { Text, Panel, Bar, Button, Joystick }

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
 * content and a Button's label; [value] is a Bar's fill from 0 to 1. A Joystick is a round pad of
 * [width] across whose knob [color] is dragged within it.
 *
 * [action] names what an element does for the game, such as `move` or `jump`; the game or runtime
 * decides what each name means. A [touchOnly] element is drawn only where touch controls are shown.
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
    var action: String = ""
    var touchOnly: Boolean = false

    private var pressed = false
    internal val interactions = InteractionSource()

    /** A Joystick's deflection from -1 to 1 each way; up is negative [stickY], as on screen. */
    var stickX: Float = 0f
        private set
    var stickY: Float = 0f
        private set

    /** True once for each tap on a Button since the last call; game code polls it. */
    fun consumePress(): Boolean = pressed.also { pressed = false }

    /** Whether a Button is being held down right now. */
    val isHeld: Boolean get() = interactions.isPressed

    internal fun press() {
        pressed = true
    }

    /** Moves a Joystick's knob by a drag of ([dx], [dy]) in the same units as [radius]. */
    internal fun dragStick(dx: Float, dy: Float, radius: Float) {
        knobX += dx
        knobY += dy
        val length = sqrt(knobX * knobX + knobY * knobY)
        if (length > radius) {
            knobX *= radius / length
            knobY *= radius / length
        }
        stickX = knobX / radius
        stickY = knobY / radius
    }

    internal fun releaseStick() {
        knobX = 0f
        knobY = 0f
        stickX = 0f
        stickY = 0f
    }

    internal var knobX: Float = 0f
        private set
    internal var knobY: Float = 0f
        private set

    companion object {
        const val DEFAULT_INSET = 16f
        const val DEFAULT_WIDTH = 200f
        const val DEFAULT_HEIGHT = 40f
        const val DEFAULT_FONT_SIZE = 18f
    }
}
