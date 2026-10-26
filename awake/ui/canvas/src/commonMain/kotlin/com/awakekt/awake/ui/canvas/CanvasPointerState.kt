/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.ui.focus.FocusRequester
import com.awakekt.awake.core.input.HeldStep
import com.awakekt.awake.core.math2d.Vec2
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * What the player is doing to one canvas element right now: its taps, hover, drag and drop, wheel,
 * clicked link and keyboard focus, as [CanvasOverlay] records them for game code to read. A widget's
 * own live input, a stick's deflection or an action button's aim, is read from here too, through
 * [CanvasPointerState.stickX] and the readers beside it.
 *
 * It is runtime state only: never saved, and a scene loads every element with a fresh one. The
 * `consume` readers take what they report, so a second reader of the same event sees nothing; a
 * reader that must not take from another reads [presses].
 */
@Suppress("TooManyFunctions") // One small reader per input an element reports.
class CanvasPointerState {
    /** What times presses; a test swaps it for a `TestTimeSource`. */
    internal var clock: TimeSource = TimeSource.Monotonic

    internal val interactions = InteractionSource()

    /** Where the press [CanvasOverlay] is turning into a tap began, in the element's own units. */
    internal var pressStart: Vec2? = null

    private var pressPoint: Vec2? = null
    private var pressed = false
    private var doublePressed = false
    private var lastPress: TimeMark? = null

    /**
     * How many times the element has been tapped since it was made, never reset. Reading it takes
     * nothing: a reader keeps the count it saw last, and each tap since is one more.
     */
    var presses: Int = 0
        private set

    /** Whether the element was right-clicked since the last [consumeSecondaryPress]. */
    var secondaryPressed: Boolean = false
        private set

    /** True once for each tap since the last call; it takes the tap, as [presses] does not. */
    fun consumePress(): Boolean = pressed.also { pressed = false }

    /**
     * Where the tap [consumePress] would report landed, in the element's own units from its
     * top-left, with any `style.rotation` undone, then takes it as [consumePress] does; null when
     * there is none.
     */
    fun consumePressAt(): Vec2? = if (consumePress()) pressPoint else null

    /** True once for each double-click since the last call: a press within [CanvasElement.DOUBLE_CLICK_SECONDS] of the one before. */
    fun consumeDoublePress(): Boolean = doublePressed.also { doublePressed = false }

    /** True once for each secondary (right-button) press since the last call. */
    fun consumeSecondaryPress(): Boolean = secondaryPressed.also { secondaryPressed = false }

    /** Whether the pointer is over the element right now. */
    val isHovered: Boolean get() = interactions.isHovered

    /** Whether the element is being held down, or a stick touched, right now. */
    val isHeld: Boolean get() = interactions.isPressed || stick.pointer != null

    internal fun press() {
        pressed = true
        pressPoint = pressStart
        presses++
        val previous = lastPress
        if (previous != null && previous.elapsedNow() <= CanvasElement.DOUBLE_CLICK_SECONDS.seconds) {
            doublePressed = true
            // A third press starts a new pair rather than making a second double-click.
            lastPress = null
        } else {
            lastPress = clock.markNow()
        }
    }

    internal fun secondaryPress() {
        secondaryPressed = true
    }

    private var wheelAccumulated = 0f

    /** Adds to the wheel collected since the last [consumeWheel]; [CanvasOverlay] calls this. */
    internal fun addWheel(delta: Float) {
        wheelAccumulated += delta
    }

    /** The wheel collected since the last call, in the same sign and units as a frame's raw scroll delta, then zero. */
    fun consumeWheel(): Float = wheelAccumulated.also { wheelAccumulated = 0f }

    /** Whether the element is being dragged right now; a ghost follows the pointer while it is. */
    var dragging: Boolean = false
        internal set

    /** Where the drag sits right now, in canvas units; what [CanvasOverlay] draws the ghost at. */
    internal var dragPointCanvasX: Float = 0f
    internal var dragPointCanvasY: Float = 0f

    /** [CanvasOverlay] sets this to the source on release over a drop target; [dropped] reads it. */
    internal var droppedSource: CanvasElement? = null

    /** The element last dropped on this drop target since the last read, or null; read once. */
    val dropped: CanvasElement?
        get() = droppedSource.also { droppedSource = null }

    /** [CanvasOverlay] sets this on release over no drop target; [droppedOutside] reads it. */
    internal var droppedOutsideAt: Vec2? = null

    /** Where the dragged element was released over no drop target, in canvas units, since the last read, or null; read once. */
    val droppedOutside: Vec2?
        get() = droppedOutsideAt.also { droppedOutsideAt = null }

    /** The key of a clicked `[[key|shown words]]` span, until [consumeLink] reads it; [CanvasOverlay] writes this. */
    internal var linkKey: String? = null

    /** The key of a `[[key|shown words]]` span clicked since the last call, then null. */
    fun consumeLink(): String? = linkKey.also { linkKey = null }

    /** A TextField's live content and caret, kept in step with its text by [CanvasElement.syncTextField]. */
    internal val textFieldState = TextFieldState()

    /** Points a TextField at the keyboard; what [focused] queues a request or a free against. */
    internal val focusRequester = FocusRequester()

    /** The text as of the last sync, so a system's write to it can be told from typing. */
    internal var lastSyncedText: String? = null

    internal var submittedText: String? = null

    /** Set when the field gains focus, until the same frame's composition reads it, so that frame's Enter does not submit. */
    internal var focusGainedThisFrame: Boolean = false

    /**
     * Whether a TextField holds the keyboard right now. Setting it asks for what a click on the
     * field, or elsewhere, would: a request or a free applied before the next frame. Anything taking
     * focus away writes this back, so it always answers for the field's real state.
     */
    var focused: Boolean = false
        set(value) {
            field = value
            if (value) focusRequester.requestFocus() else focusRequester.freeFocus()
        }

    /**
     * The line a TextField submitted by Enter since the last call, or null when nothing was. Submitting
     * frees the field's focus, so gameplay keys work again the next frame.
     */
    fun consumeSubmit(): String? = submittedText.also { submittedText = null }

    /** A Joystick's live state, fed by its pointer. */
    internal val stick = CanvasStick { clock }

    /** A Joystick's deflection from -1 to 1 to the right. */
    val stickX: Float get() = stick.x

    /** A Joystick's vertical deflection from -1 to 1; up is negative, down is positive, as on screen. */
    val stickY: Float get() = stick.y

    /** Whether a Joystick's knob is out past its [CanvasJoystick.sprintThreshold] right now. */
    val sprinting: Boolean get() = stick.sprinting

    /** True once each time a Joystick starts [sprinting], since the last call. */
    fun consumeSprintStart(): Boolean = stick.sprintStarted.also { stick.sprintStarted = false }

    /** A LookArea's live thumbs, look and zoom. */
    internal val lookState = CanvasLookState()

    /** How far a LookArea has been swiped since the last call, in dp times its sensitivity, then zero. */
    fun consumeLook(): Vec2 = lookState.consumeLook()

    /** The factor a LookArea's pinch has spread by since the last call, above 1 to zoom in and 1 when there was none. */
    fun consumeZoom(): Float = lookState.consumeZoom()

    /** An action button's live press. */
    internal val buttonPress = CanvasButtonPress()

    /** True once each time an action button goes down while usable, since the last call. */
    fun consumeDown(): Boolean = buttonPress.down.also { buttonPress.down = false }

    /** True once each time an action button is let go after a usable press, since the last call. */
    fun consumeRelease(): Boolean = buttonPress.released.also { buttonPress.released = false }

    /** True once each time an action button is held for its [CanvasButton.longPressSeconds], since the last call. */
    fun consumeLongPress(): Boolean = buttonPress.longPress.also { buttonPress.longPress = false }

    /** True once each time an aimed action button is let go over its cancel zone, since the last call. */
    fun consumeAimCancel(): Boolean = buttonPress.aimCancelled.also { buttonPress.aimCancelled = false }

    /** Whether an action button with [CanvasButton.aim] is held and being dragged to aim right now. */
    val aiming: Boolean get() = buttonPress.aimingPress

    /** An aimed action button's aim, from -1 to 1, up negative: while [aiming], and after a release the aim it fired with. */
    val aimX: Float get() = buttonPress.aimX

    /** The vertical half of [aimX]; up is negative. */
    val aimY: Float get() = buttonPress.aimY

    /** Whether an aimed action button is held over its cancel zone, so letting go now cancels. */
    val aimCancelling: Boolean get() = buttonPress.cancelling

    /** The key of the target [CanvasButtonWidget.aimLock] locked the aim onto, or null while none is. */
    val aimTarget: String? get() = buttonPress.target

    /** How long an action button has been held, in seconds; 0 while it is not. */
    val heldSeconds: Float get() = buttonPress.heldSeconds()

    /** A Stepper's two buttons: their presses, and how long each has been held. */
    internal val minusPress = InteractionSource()
    internal val plusPress = InteractionSource()
    internal val minusHold = HeldStep()
    internal val plusHold = HeldStep()

    /** Each skin slot's fade between states, by slot name. */
    internal val slotTransitions = HashMap<String, SlotTransition>()

    private var skinTheme: CanvasControlTheme? = null
    private var skinOwn: CanvasControlSkin? = null
    private var skinResolved: CanvasControlSkin? = null
    private var skinReady = false

    /** The element's [skin] over [theme], worked out again only when either changes. */
    internal fun resolvedSkin(theme: CanvasControlTheme?, skin: CanvasControlSkin?): CanvasControlSkin? {
        if (!skinReady || skinTheme !== theme || skinOwn !== skin) {
            skinResolved = theme.resolve(skin)
            skinTheme = theme
            skinOwn = skin
            skinReady = true
        }
        return skinResolved
    }
}
