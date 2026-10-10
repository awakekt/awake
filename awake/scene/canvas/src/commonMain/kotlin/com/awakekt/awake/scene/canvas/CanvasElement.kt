/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.scene.document.SceneVec3
import kotlinx.serialization.Serializable
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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
 * [action] names what an element does for the game, such as `move` or `jump`, and [doubleAction]
 * what a Button does when double-clicked; the game or runtime decides what each name means. A [touchOnly] element is drawn only where touch controls are shown.
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

    /** What a Button does when double-clicked: two presses within [DOUBLE_CLICK_SECONDS]. */
    var doubleAction: String = ""

    /** If true, element is displayed only when touch controls are active. */
    var touchOnly: Boolean = false

    /** How the element looks beyond its colours: images, and their frames and fills. */
    var style: CanvasStyle = CanvasStyle()

    /** Where a Text's or Button's text sits; null keeps the kind's own, top-left or centred. */
    var textAlign: CanvasAnchor? = null

    /** The name of a node this element follows on screen; empty follows none. */
    var follow: String = ""

    /** A world-space offset from the followed node, such as above its head. */
    var followOffset: SceneVec3 = SceneVec3()

    /** Whether the element covers the screen rectangle of the followed node's meshes. */
    var followBounds: Boolean = false

    /** How far, in dp, a [followBounds] box reaches past the node's rectangle on each side; negative pulls it in. */
    var followOutset: Float = 0f

    /** How the element places its children itself; null anchors each child by hand. */
    var layout: CanvasLayout? = null

    /** In a parent with a layout, this element's share of its line's leftover space. */
    var grow: Float = 0f

    /** Game state the element shows without code; while it reads, it stands in for [value] and [text]. */
    var bind: CanvasBinding? = null

    private var pressed = false
    private var doublePressed = false
    private var lastPress: TimeMark? = null

    /** What times presses; a test swaps it for a `TestTimeSource`. */
    internal var clock: TimeSource = TimeSource.Monotonic
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

    /**
     * True once for each double-click on a Button since the last call: a press within
     * [DOUBLE_CLICK_SECONDS] of the one before. Its second press is also a press for [consumePress].
     */
    fun consumeDoublePress(): Boolean = doublePressed.also { doublePressed = false }

    /** Whether a Button is being held down right now. */
    val isHeld: Boolean get() = interactions.isPressed

    internal fun press() {
        pressed = true
        val previous = lastPress
        if (previous != null && previous.elapsedNow() <= DOUBLE_CLICK_SECONDS.seconds) {
            doublePressed = true
            // A third press starts a new pair rather than making a second double-click.
            lastPress = null
        } else {
            lastPress = clock.markNow()
        }
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

        /** How soon a second press makes a double-click, as the common desktop default. */
        const val DOUBLE_CLICK_SECONDS = 0.5
    }
}
