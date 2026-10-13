/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.ui.focus.FocusRequester
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
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

    /** A single-line field the player types into. */
    TextField,

    /** Virtual touch analog joystick. */
    Joystick,

    /** A picture: its style's [CanvasStyle.image]. */
    Image,

    /** What a camera sees: the scene from the camera on the node [CanvasElement.camera] names, under the element's children. */
    View,
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
 * [width] across whose knob [color] is dragged within it. A View shows what the camera on the node
 * [camera] names sees.
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

    /**
     * Whether this element takes the mouse wheel while the pointer is over it or its children,
     * instead of leaving it for gameplay. The innermost such element under the pointer takes it;
     * [consumeWheel] reads what it collected.
     */
    var wheel: Boolean = false

    /** A View's camera: the name of a node with a camera, whose picture it shows; empty shows none. */
    var camera: String = ""

    /**
     * Whether a Text element's `[[key|shown words]]` spans draw as links.
     *
     * False for text a game cannot trust, such as player chat: `[[` and `]]` then draw as typed and
     * never report a key, so a player cannot make their own line clickable.
     */
    var links: Boolean = true

    private var wheelAccumulated = 0f

    /** Adds to the wheel collected since the last [consumeWheel]; [SceneCanvas] calls this. */
    internal fun addWheel(delta: Float) {
        wheelAccumulated += delta
    }

    /**
     * The wheel collected since the last call, in the same sign and units as a frame's raw scroll
     * delta, then zero.
     */
    fun consumeWheel(): Float = wheelAccumulated.also { wheelAccumulated = 0f }

    /** Whether this element can be picked up and dragged onto a [dropTarget] element or the world. */
    var draggable: Boolean = false

    /** Whether a dragged element released over this one is reported here; see [dropped]. */
    var dropTarget: Boolean = false

    /** Whether this element is being dragged right now; a ghost follows the pointer while it is. */
    var dragging: Boolean = false
        internal set

    /** Where the drag sits right now, in canvas units; what [SceneCanvas] draws the ghost at. */
    internal var dragPointCanvasX: Float = 0f
    internal var dragPointCanvasY: Float = 0f

    /** [SceneCanvas] sets this to the source on release over a drop target; [dropped] reads it. */
    internal var droppedSource: CanvasElement? = null

    /**
     * The element last dropped on this [dropTarget] since the last read, or null -- a property
     * consumed once, the same way [droppedOutside] is.
     */
    val dropped: CanvasElement?
        get() = droppedSource.also { droppedSource = null }

    /** [SceneCanvas] sets this on release over no drop target; [droppedOutside] reads it. */
    internal var droppedOutsideAt: Vec2? = null

    /**
     * Where this [draggable] element was released over no drop target, in canvas units, since the
     * last read, or null -- a property consumed once, the same way [consumeWheel] is.
     */
    val droppedOutside: Vec2?
        get() = droppedOutsideAt.also { droppedOutsideAt = null }

    /** The key of a clicked `[[key|shown words]]` span, until [consumeLink] reads it; [SceneCanvas] writes this. */
    internal var linkKey: String? = null

    private var pressed = false
    private var doublePressed = false
    private var lastPress: TimeMark? = null

    /** What times presses; a test swaps it for a `TestTimeSource`. */
    internal var clock: TimeSource = TimeSource.Monotonic
    internal val interactions = InteractionSource()

    /** A TextField's live content and caret; kept in step with [text] by [syncTextField]. */
    internal val textFieldState = TextFieldState()

    /** Points a TextField at the keyboard; what [focused] queues a request or a free against. */
    internal val focusRequester = FocusRequester()

    /** [text] as of the last [syncTextField], so a system's write to it can be told from typing. */
    private var lastSyncedText: String? = null

    private var submittedText: String? = null

    /**
     * Whether a TextField holds the keyboard right now.
     *
     * A system setting this true or false asks for the same thing a click on the field, or
     * elsewhere, would: the write queues a request or a free on [focusRequester], applied before
     * the scene's next frame. A click focusing the field, or anything taking focus away, writes
     * this back through `onFocusChanged`, so it always answers for the field's real state.
     */
    var focused: Boolean = false
        set(value) {
            field = value
            if (value) focusRequester.requestFocus() else focusRequester.freeFocus()
        }

    /**
     * Set by [TextFieldView]'s focus callback when this element gains focus, until the same frame's
     * composition reads and clears it. Lets that frame's Enter -- the one a system read to focus the
     * field in the first place -- skip [submit] instead of submitting the field the instant it opens.
     */
    internal var focusGainedThisFrame: Boolean = false

    /**
     * The line submitted by Enter since the last call, or null when nothing was; game code polls it,
     * as [consumePress] polls a Button. The text is the field's live state as of the submit, so a
     * character typed the same frame Enter was pressed is included even though it has not reached
     * [text] yet -- that happens at the next composition. Submitting frees the field's focus, so
     * gameplay keys work again the next frame.
     */
    fun consumeSubmit(): String? = submittedText.also { submittedText = null }

    internal fun submit() {
        submittedText = textFieldState.text
        focused = false
    }

    /**
     * Keeps [textFieldState] and [text] pointed at the same string, in whichever direction
     * changed: typing updates [textFieldState] first, so that reaches [text]; a system writing
     * [text] directly -- clearing it after a send -- is not [lastSyncedText], so that reaches
     * [textFieldState] instead, caret at the end.
     */
    internal fun syncTextField() {
        if (text != lastSyncedText) {
            textFieldState.setText(text)
        } else if (textFieldState.text != text) {
            text = textFieldState.text
        }
        lastSyncedText = text
    }

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

    /** Whether the pointer is over this element right now. */
    val isHovered: Boolean get() = interactions.isHovered

    /**
     * Where this element was placed last frame, in the canvas's own units -- a box from the
     * canvas's top-left, in the same units as [offsetX] and [width]. Null when it was not drawn:
     * not [visible], hidden by [touchOnly], or a [follow] with nowhere to land this frame.
     *
     * A child's box is already in canvas coordinates, not relative to its parent.
     */
    var screenBounds: Rectangle? = null
        internal set

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

/**
 * The key of a `[[key|shown words]]` span clicked in [CanvasElement.text] since the last call, then
 * null.
 *
 * @return The clicked span's key, or null when none was clicked since the last call.
 */
fun CanvasElement.consumeLink(): String? = linkKey.also { linkKey = null }

/** A run of a Text element's words, plain or a clickable `[[key|shown words]]` span. */
internal sealed class CanvasTextSpan {
    /** Words drawn in the Text's own colour. */
    data class Plain(val text: String) : CanvasTextSpan()

    /** Words drawn in the link colour; a click on them reports [key] through [CanvasElement.consumeLink]. */
    data class Link(val key: String, val text: String) : CanvasTextSpan()
}

/** `[[key|shown words]]`, key and words each everything but `|` and `]]`. */
private val LINK_SPAN = Regex("""\[\[([^|\]]+)\|([^]]+)]]""")

/**
 * Splits [text] on `[[key|shown words]]` spans. Text with none comes back as its one [CanvasTextSpan.Plain].
 *
 * `[[` is reserved: there is no escape for a literal `[[` in plain text.
 */
internal fun parseCanvasTextSpans(text: String): List<CanvasTextSpan> {
    if ("[[" !in text) return listOf(CanvasTextSpan.Plain(text))
    return buildList {
        var last = 0
        for (match in LINK_SPAN.findAll(text)) {
            if (match.range.first > last) add(CanvasTextSpan.Plain(text.substring(last, match.range.first)))
            add(CanvasTextSpan.Link(match.groupValues[1], match.groupValues[2]))
            last = match.range.last + 1
        }
        if (last < text.length) add(CanvasTextSpan.Plain(text.substring(last)))
    }
}
