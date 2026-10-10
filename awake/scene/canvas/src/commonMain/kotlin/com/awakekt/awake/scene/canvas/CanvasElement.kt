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
enum class CanvasElementKind {
    /** Plain textual display element. */
    Text,
    /** Static filled background panel or container. */
    Panel,
    /** Horizontal progress or status bar. */
    Bar,
    /** Interactive clickable button. */
    Button,
    /** Virtual touch analog joystick. */
    Joystick,
    /** A picture: its style's [CanvasStyle.image]. */
    Image,
}

/** The screen point a [CanvasElement] is pinned to; its offset moves it inward from there. */
@Serializable
enum class CanvasAnchor {
    /** Top-left screen corner. */
    TopLeft,
    /** Top edge centered horizontally. */
    TopCenter,
    /** Top-right screen corner. */
    TopRight,
    /** Left edge centered vertically. */
    CenterLeft,
    /** Exact center of the screen. */
    Center,
    /** Right edge centered vertically. */
    CenterRight,
    /** Bottom-left screen corner. */
    BottomLeft,
    /** Bottom edge centered horizontally. */
    BottomCenter,
    /** Bottom-right screen corner. */
    BottomRight,
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
    /** The rendering style and semantic type of this element. */
    var kind: CanvasElementKind = CanvasElementKind.Text

    /** Screen anchor point determining reference origin. */
    var anchor: CanvasAnchor = CanvasAnchor.TopLeft

    /** Inset offset in density-independent pixels along the X axis. */
    var offsetX: Float = DEFAULT_INSET

    /** Inset offset in density-independent pixels along the Y axis. */
    var offsetY: Float = DEFAULT_INSET

    /** Width in density-independent pixels. */
    var width: Float = DEFAULT_WIDTH

    /** Height in density-independent pixels. */
    var height: Float = DEFAULT_HEIGHT

    /** Textual content for text displays and button labels. */
    var text: String = ""

    /** Font size in scale-independent pixels. */
    var fontSize: Float = DEFAULT_FONT_SIZE

    /** Hex color code for foreground content or knob. */
    var color: String = "#FFFFFF"

    /** Hex color code for element background. */
    var background: String = "#00000000"

    /** Normalized fill value for progress bars (0.0 to 1.0). */
    var value: Float = 1f

    /** Z-ordering layer index for overlapping elements. */
    var order: Int = 0

    /** Whether this element is rendered. */
    var visible: Boolean = true

    /** Action trigger identifier associated with button activation. */
    var action: String = ""

    /** If true, element is displayed only when touch controls are active. */
    var touchOnly: Boolean = false

    /** How the element looks beyond its colours: images, and their frames and fills. */
    var style: CanvasStyle = CanvasStyle()

    private var pressed = false
    internal val interactions = InteractionSource()

    /** A Joystick's deflection from -1 to 1 each way; up is negative [stickY], as on screen. */
    var stickX: Float = 0f
        private set

    /** A Joystick's vertical deflection from -1 to 1; up is negative, down is positive. */
    var stickY: Float = 0f
        private set

    /**
     * True once for each tap on a Button since the last call; game code polls it.
     *
     * @return True if a tap event was registered and unconsumed.
     */
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

    /** Default dimensions and styling metrics for canvas elements. */
    companion object {
        /** Default edge inset offset in dp. */
        const val DEFAULT_INSET = 16f
        /** Default element width in dp. */
        const val DEFAULT_WIDTH = 200f
        /** Default element height in dp. */
        const val DEFAULT_HEIGHT = 40f
        /** Default font size in sp. */
        const val DEFAULT_FONT_SIZE = 18f
    }
}
