/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.ui.focus.FocusRequester
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.core.text.unicode.TextLengthUnit
import kotlinx.serialization.Serializable
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

    /**
     * A region where a swipe looks and a pinch zooms, such as the right half of the screen, read
     * through [CanvasElement.consumeLook] and [CanvasElement.consumeZoom] as its [CanvasElement.look] says.
     */
    LookArea,
}

/**
 * The screen point a [CanvasElement] is pinned to. Its offset moves it inward from an edge, and
 * along a centred axis right or down, or left or up when negative.
 *
 * On a centred axis the element is placed in whole pixels of its box, the canvas or the element it
 * is drawn in, at the canvas's density and scale. Its size, and twice its offset, are each rounded
 * to whole pixels, halves up; the doubled offset goes before the element when positive and after it
 * when negative. The pixels the box has to spare around the two are halved and rounded down, so an
 * odd pixel left over goes after them, on the right or at the bottom: the element sits half a pixel
 * left of or above the exact centre. A host that works out where a centred element lands, such as
 * an editor catching it on a guide, rounds the same way to match where it is drawn.
 */
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
 * A screen-space UI element, drawn over the game by [CanvasOverlay]. A host places it on whatever
 * it uses to hold game objects; `awake:scene:canvas`'s `SceneCanvas` does this for an ECS entity.
 *
 * Sizes and offsets are in dp. Colours are `#RRGGBB` or `#RRGGBBAA`. [text] is a Text element's
 * content and a Button's label; [value] is a Bar's fill from 0 to 1. A Joystick is a round pad of
 * [width] across whose knob [color] is dragged within it. A View shows what the camera on the node
 * [camera] names sees.
 *
 * [action] names what an element does for the game, such as `move` or `jump`, and [doubleAction]
 * what a Button does when double-clicked; the game or runtime decides what each name means. A [touchOnly] element is drawn only where touch controls are shown.
 */
@Suppress("TooManyFunctions") // One small reader per input an element reports.
class CanvasElement {
    /** The rendering style and semantic type of this element. */
    var kind: CanvasElementKind = CanvasElementKind.Text

    /** Screen anchor point determining reference origin. */
    var anchor: CanvasAnchor = CanvasAnchor.TopLeft

    /**
     * How far, in dp, the element moves from its anchor along the X axis: inward from a left or
     * right edge, where a negative offset is held at the edge, and right of centre on a centred
     * axis, or left of it when negative. A centred element lands on whole pixels; see [CanvasAnchor]
     * for how it rounds.
     */
    var offsetX: Float = DEFAULT_INSET

    /**
     * How far, in dp, the element moves from its anchor along the Y axis: inward from a top or
     * bottom edge, where a negative offset is held at the edge, and below centre on a centred axis,
     * or above it when negative. A centred element lands on whole pixels; see [CanvasAnchor] for how
     * it rounds.
     */
    var offsetY: Float = DEFAULT_INSET

    /** Width in density-independent pixels. */
    var width: Float = DEFAULT_WIDTH

    /** Height in density-independent pixels. */
    var height: Float = DEFAULT_HEIGHT

    /** Textual content for text displays and button labels. */
    var text: String = ""

    /**
     * The key of a Text's or Button's words in the project's string tables, such as `hud.greeting`;
     * empty shows [text]. While it is set, the words are the key's text in the active locale of the
     * `Strings` [CanvasOverlay] was given, with [textArgs] put into its placeholders, and they change
     * when the locale does. A key no table has shows as the key and is logged once.
     */
    var textKey: String = ""

    /**
     * Values for [textKey]'s `{name}`, `plural` and `select` placeholders, by name, such as `count`
     * to `3`; a number placeholder reads a value written as a number. A system sets them from code.
     * A field [bind] reads into an argument, through [CanvasBinding.textArgs], takes the place of the
     * value of the same name here.
     */
    var textArgs: Map<String, String> = emptyMap()

    /** Font size in scale-independent pixels. */
    var fontSize: Float = DEFAULT_FONT_SIZE

    /** Hex color code for foreground content or knob. */
    var color: String = "#FFFFFF"

    /** Hex color code for element background. */
    var background: String = "#00000000"

    /** Normalized fill value for progress bars (0.0 to 1.0). */
    var value: Float = 1f

    /**
     * Z-ordering layer index for overlapping elements: a higher one draws over a lower one and takes
     * the pointer from it. Of the same order, a host's own order decides, for a scene its hierarchy.
     */
    var order: Int = 0

    /** Whether this element is rendered. */
    var visible: Boolean = true

    /** Action trigger identifier associated with button activation. */
    var action: String = ""

    /** What a Button does when double-clicked: two presses within [DOUBLE_CLICK_SECONDS]. */
    var doubleAction: String = ""

    /** If true, element is displayed only when touch controls are active. */
    var touchOnly: Boolean = false

    /**
     * Whether an anchored element on the canvas itself keeps inside the safe area [CanvasOverlay] is
     * given, clear of a notch, rounded corners or a home indicator: its anchor and offsets are then
     * measured from the safe area's edges. False measures them from the canvas's own edges, for a
     * backdrop that should reach under a notch. An element inside another, or one that follows a
     * node, is placed the same either way.
     */
    var safeArea: Boolean = true

    /** How the element looks beyond its colours: images, and their frames and fills. */
    var style: CanvasStyle = CanvasStyle()

    /** Where a Text's or Button's text sits; null keeps the kind's own, top-left or centred. */
    var textAlign: CanvasAnchor? = null

    /** The name of a node this element follows on screen; empty follows none. */
    var follow: String = ""

    /** A world-space offset from the followed node, such as above its head. */
    var followOffset: Vec3f = Vec3f(0f, 0f, 0f)

    /** Whether the element covers the screen rectangle of the followed node's meshes. */
    var followBounds: Boolean = false

    /** How far, in dp, a [followBounds] box reaches past the node's rectangle on each side; negative pulls it in. */
    var followOutset: Float = 0f

    /**
     * How a [follow]er behaves with many on screen: culling, ranking against a [CanvasFollowLimit],
     * scale and fade by distance, and holding to the screen's edge. Null follows
     * [CanvasFollowRules.Default]: culled off screen and hidden behind the camera.
     */
    var followRules: CanvasFollowRules? = null

    /**
     * Whether this element, inside a [follow]er, is the arrow of one held to the screen's edge: drawn
     * only while its parent is [pinned], centred on its parent's edge toward the target and turned
     * to point at it, its picture pointing right before it turns; its `style.rotation` adds to the
     * turn, so art that points up takes 90.
     */
    var followArrow: Boolean = false

    /**
     * How important this follower is right now, set from code each frame, for a [CanvasFollowLimit]
     * that ranks by [CanvasFollowRank.Score]: higher stays. A game scores by whatever it knows, such
     * as threat or a quest target.
     */
    var followScore: Float = 0f

    /** Whether this [follow]er was last drawn held to the screen's edge, its target off screen or behind the camera. */
    var pinned: Boolean = false
        internal set

    /**
     * While [pinned], which way the target lies from the element, in degrees clockwise from the
     * right, as the screen has it.
     */
    var pinAngle: Float = 0f
        internal set

    /** A [followArrow]'s turn from its follower's [pinAngle]; [CanvasOverlay] sets it as it draws the arrow. */
    internal var arrowAngle: Float = 0f

    /** How far the followed point was from the camera last frame, in world units; 0 when the host cannot tell. */
    var followDistance: Float = 0f
        internal set

    /** How the element places its children itself; null anchors each child by hand. */
    var layout: CanvasLayout? = null

    /**
     * Places the element's children as a button cluster, the first on a pivot and the rest on an arc
     * around it; it takes the place of [layout]. Null places them as [layout] says.
     */
    var cluster: CanvasCluster? = null

    /** How a LookArea turns a swipe into a look and a pinch into a zoom; null behaves as [CanvasLook.Default]. */
    var look: CanvasLook? = null

    /** A LookArea's live thumbs, look and zoom. */
    internal val lookState = CanvasLookState()

    /**
     * How far a LookArea has been swiped since the last call, in dp times its [CanvasLook.sensitivity],
     * right and down positive unless inverted, then zero.
     */
    fun consumeLook(): Vec2 = lookState.consumeLook()

    /**
     * The factor a LookArea's pinch has spread by since the last call, above 1 to zoom in and 1 when
     * there was none, then 1.
     */
    fun consumeZoom(): Float = lookState.consumeZoom()

    /** In a parent with a layout, this element's share of its line's leftover space. */
    var grow: Float = 0f

    /**
     * Game state the element shows without code; while it reads, it stands in for [value] and [text],
     * and its [CanvasBinding.textArgs] for [textArgs].
     */
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

    /**
     * A TextField's password mask: one character drawn for each typed one; empty draws the text.
     *
     * A masked field keeps [text] as typed, refuses copy and cut, and reads as a password to an
     * accessibility service and the platform keyboard. Clearing it shows the password.
     */
    var mask: String = ""

    /**
     * The most a TextField lets the player type, counted in [maxLengthUnit]; 0, the default, for no
     * limit.
     *
     * Typing, pasting and an IME stop at the limit, and never inside a character: an emoji or a flag
     * goes in whole or not at all. A system writing [text] is not limited.
     */
    var maxLength: Int = 0

    /**
     * What a TextField's [maxLength] counts: the characters a player sees, by default, or code points
     * or UTF-8 bytes to match the limit a server keeps, so a line the field accepts is never refused
     * or cut there.
     */
    var maxLengthUnit: TextLengthUnit = TextLengthUnit.Graphemes

    /**
     * The font a Text's, TextField's or Button's words draw in: a project `.ttf` or `.otf` file
     * that [CanvasOverlay] was given, or a built-in family, `default` or `bitmap`.
     * Empty, a file that did not load, or a name that is neither draws in the default font.
     */
    var font: String = ""

    /**
     * Whether an Image or Panel takes a tap as a Button does: [consumePress], [consumePressAt] and
     * [presses] report it, and what is under it does not get it. False leaves it watching only.
     */
    var pressable: Boolean = false

    /** Where the press [CanvasOverlay] is turning into a tap began, in this element's own units. */
    internal var pressStart: Vec2? = null

    private var pressPoint: Vec2? = null

    private var wheelAccumulated = 0f

    /** Adds to the wheel collected since the last [consumeWheel]; [CanvasOverlay] calls this. */
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

    /** Where the drag sits right now, in canvas units; what [CanvasOverlay] draws the ghost at. */
    internal var dragPointCanvasX: Float = 0f
    internal var dragPointCanvasY: Float = 0f

    /** [CanvasOverlay] sets this to the source on release over a drop target; [dropped] reads it. */
    internal var droppedSource: CanvasElement? = null

    /**
     * The element last dropped on this [dropTarget] since the last read, or null -- a property
     * consumed once, the same way [droppedOutside] is.
     */
    val dropped: CanvasElement?
        get() = droppedSource.also { droppedSource = null }

    /** [CanvasOverlay] sets this on release over no drop target; [droppedOutside] reads it. */
    internal var droppedOutsideAt: Vec2? = null

    /**
     * Where this [draggable] element was released over no drop target, in canvas units from the
     * canvas's top-left as [screenBounds] is, since the last read, or null -- a property consumed
     * once, the same way [consumeWheel] is.
     */
    val droppedOutside: Vec2?
        get() = droppedOutsideAt.also { droppedOutsideAt = null }

    /** The key of a clicked `[[key|shown words]]` span, until [consumeLink] reads it; [CanvasOverlay] writes this. */
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
     * character typed the same frame Enter was pressed is included, and [text] holds the same line
     * from the submit on: a system that then clears [text] empties the field. Submitting frees the
     * field's focus, so gameplay keys work again the next frame.
     */
    fun consumeSubmit(): String? = submittedText.also { submittedText = null }

    internal fun submit() {
        submittedText = textFieldState.text
        // Synced here rather than at the next composition. A line typed the same frame as its Enter
        // hasn't reached [text] yet, so a system clearing [text] would look like no change at all,
        // and the next sync would put the sent line back.
        text = textFieldState.text
        lastSyncedText = text
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

    /**
     * How a Joystick moves and what it reports: its mode, reach, dead zone, response, directions,
     * sprint and idle fade. Null behaves as [CanvasJoystick.Default].
     */
    var joystick: CanvasJoystick? = null

    /** A Joystick's live state, fed by its pointer. */
    internal val stick = CanvasStick { clock }

    /** A Joystick's deflection from -1 to 1 each way; up is negative [stickY], as on screen. */
    val stickX: Float get() = stick.x

    /** A Joystick's vertical deflection from -1 to 1; up is negative, down is positive. */
    val stickY: Float get() = stick.y

    /** Whether a Joystick's knob is out past its [CanvasJoystick.sprintThreshold] right now. */
    val sprinting: Boolean get() = stick.sprinting

    /** True once each time a Joystick starts [sprinting], since the last call; game code polls it. */
    fun consumeSprintStart(): Boolean = stick.sprintStarted.also { stick.sprintStarted = false }

    /**
     * True once for each tap on a Button since the last call; game code polls it.
     *
     * It takes the tap: a second reader polling the same Button sees nothing. A reader that must not
     * take a tap from another, such as a blueprint beside a Button's [action], reads [presses].
     *
     * @return True if a tap event was registered and unconsumed.
     */
    fun consumePress(): Boolean = pressed.also { pressed = false }

    /**
     * Where the tap [consumePress] would report landed, then takes it as [consumePress] does; null
     * when there is none.
     *
     * In this element's own units, from its top-left: the same units as [width], whatever the
     * canvas's scale. On an element with a `style.rotation`, it is the point on the turned picture,
     * as it would be with the rotation undone.
     */
    fun consumePressAt(): Vec2? = if (consumePress()) pressPoint else null

    /**
     * How many times this Button has been tapped since it was made, never reset.
     *
     * Reading it takes nothing: a reader keeps the count it saw last, and each tap since is one more.
     * Any number of readers so react to the same tap, alongside [consumePress], in whatever order
     * they run.
     */
    var presses: Int = 0
        private set

    /**
     * True once for each double-click on a Button since the last call: a press within
     * [DOUBLE_CLICK_SECONDS] of the one before. Its second press is also a press for [consumePress].
     */
    fun consumeDoublePress(): Boolean = doublePressed.also { doublePressed = false }

    /**
     * How a Button acts as an on-screen action button: when it fires, a long press, a release, and
     * whether a cooldown holds it back. Null is a plain button, tapped on release.
     */
    var button: CanvasButton? = null

    /**
     * Whether a Button can be pressed. A disabled one still takes a press, so it does not reach what
     * is under it, but reports nothing and draws in its style's `disabled` look.
     */
    var enabled: Boolean = true
        set(value) {
            val wasUsable = usable
            field = value
            noteReady(wasUsable)
        }

    /**
     * How much of an action button's cooldown is left, from 1, just used, to 0, ready; drawn as a
     * sweep over it. Set from code, or read each frame from [CanvasBinding.cooldown].
     */
    var cooldown: Float = 0f
        set(value) {
            val wasUsable = usable
            field = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
            noteReady(wasUsable)
        }

    /**
     * How many uses an action button has left, shown as a badge, such as a dash's charges or a
     * magazine's rounds; below 0, the default, shows none. While it is above 0 the button can be
     * used even as [cooldown] recharges the next one. Set from code, or read each frame from
     * [CanvasBinding.charges].
     */
    var charges: Int = -1
        set(value) {
            val wasUsable = usable
            field = value
            noteReady(wasUsable)
        }

    /** Whether an action button is cooling down: [cooldown] above 0 and no [charges] left to spend. */
    val coolingDown: Boolean get() = cooldown > 0f && charges <= 0

    /** Whether a Button can be pressed now: [enabled], and not held back by [coolingDown]. */
    val usable: Boolean get() = enabled && !(coolingDown && (button ?: CanvasButton.Default).blockWhileCooling)

    private var becameReady = false

    private fun noteReady(wasUsable: Boolean) {
        if (!wasUsable && usable) becameReady = true
    }

    /**
     * True once each time an action button becomes usable again, its cooldown done, a charge back
     * or it is enabled, since the last call: what a "ready" pulse or sound waits for.
     */
    fun consumeReady(): Boolean = becameReady.also { becameReady = false }

    /** An action button's live press. */
    internal val buttonPress = CanvasButtonPress()

    /** True once each time an action button goes down while usable, since the last call. */
    fun consumeDown(): Boolean = buttonPress.down.also { buttonPress.down = false }

    /** True once each time an action button is let go after a usable press, wherever the thumb was, since the last call. */
    fun consumeRelease(): Boolean = buttonPress.released.also { buttonPress.released = false }

    /** True once each time an action button is held for its [CanvasButton.longPressSeconds], since the last call. */
    fun consumeLongPress(): Boolean = buttonPress.longPress.also { buttonPress.longPress = false }

    /** Whether an action button with [CanvasButton.aim] is held and being dragged to aim right now. */
    val aiming: Boolean get() = buttonPress.aiming(this)

    /**
     * An aimed action button's aim, from -1 to 1, up negative as on screen: while [aiming], and after
     * a release the aim it fired with, until the next press. Zero inside the aim's dead zone.
     */
    val aimX: Float get() = buttonPress.aimX

    /** The vertical half of [aimX]; up is negative. */
    val aimY: Float get() = buttonPress.aimY

    /** Whether an aimed action button is held over its cancel zone, so letting go now cancels. */
    val aimCancelling: Boolean get() = buttonPress.cancelling

    /** The key of the target [aimLock] locked the aim onto, or null while none is. */
    val aimTarget: String? get() = buttonPress.target

    /**
     * Locks an aimed action button's aim onto a target while [CanvasAim.lockTarget] is set, such as
     * the one nearest the aimed line: the game knows its targets, so it answers. Null keeps the raw aim.
     */
    var aimLock: CanvasAimLock? = null

    /** True once each time an aimed action button is let go over its cancel zone, since the last call. */
    fun consumeAimCancel(): Boolean = buttonPress.aimCancelled.also { buttonPress.aimCancelled = false }

    /** How long an action button has been held, in seconds; 0 while it is not. */
    val heldSeconds: Float get() = buttonPress.heldSeconds()

    /** Whether a Button is being held down, or a Joystick touched, right now. */
    val isHeld: Boolean get() = interactions.isPressed || stick.pointer != null

    /** Whether the pointer is over this element right now. */
    val isHovered: Boolean get() = interactions.isHovered

    /**
     * Where this element was placed last frame, in the canvas's own units -- a box from the
     * canvas's top-left, in the same units as [offsetX] and [width]. Null when it was not drawn:
     * not [visible], hidden by [touchOnly], or a [follow] with nowhere to land this frame.
     *
     * A child's box is already in canvas coordinates, not relative to its parent. A canvas a host
     * draws over part of the window, such as an editor's game view, reports the same boxes as one
     * filling it: from its own corner, not the window's.
     */
    var screenBounds: Rectangle? = null
        internal set

    /**
     * Where this element was last placed, kept while the next frame is composed, when [screenBounds]
     * is cleared until it is placed again: what a guide drawn over it, such as an aim's reach, reads.
     */
    internal var placedBounds: Rectangle? = null

    internal fun press() {
        pressed = true
        pressPoint = pressStart
        presses++
        val previous = lastPress
        if (previous != null && previous.elapsedNow() <= DOUBLE_CLICK_SECONDS.seconds) {
            doublePressed = true
            // A third press starts a new pair rather than making a second double-click.
            lastPress = null
        } else {
            lastPress = clock.markNow()
        }
    }

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
