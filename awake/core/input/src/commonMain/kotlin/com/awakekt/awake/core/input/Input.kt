/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

/**
 * Every physical key this engine can be told about.
 *
 * **One vocabulary, shared by gameplay and UI.** A host maps its platform's keycodes to this once;
 * two enums would mean two mapping tables per host for the same keyboard, and they drift.
 *
 * An enum rather than Compose's `Key(keyCode: Long)` value class with per-platform `actual`
 * constants. That design leans on every target having a mature windowing toolkit whose native event
 * object can be carried around as `Any` — true for Android and AWT, false here, where the hosts are
 * GLFW, a wasm canvas, UIKit and Android and share no such type. An enum is portable, comparable
 * across targets, and testable in `commonTest` with no actuals.
 *
 * [Unknown] is the honest answer for a key a host does not map, so a caller can never name a key it
 * will never receive.
 */
enum class Key {
    /** An unmapped or unrecognized hardware key. */
    Unknown,

    // Gameplay movement and debug — the original set.
    /** The 'W' key, typically used for forward movement. */
    W,
    /** The 'A' key, typically used for left strafe movement. */
    A,
    /** The 'S' key, typically used for backward movement. */
    S,
    /** The 'D' key, typically used for right strafe movement. */
    D,
    /** The Spacebar key. */
    Space,
    /** The Escape key. */
    Escape,
    /** Function key F1. */
    F1,
    /** Function key F2. */
    F2,
    /** Function key F3. */
    F3,
    /** Function key F4. */
    F4,
    /** Function key F5. */
    F5,

    // Modifiers. Entries rather than a separate flag set, because [InputSnapshot.keysDown] already
    // models "held" exactly right -- a second representation would be a second thing to keep in
    // sync. Left and right map to the same entry: nothing in this engine distinguishes them, and
    // splitting them would double every host's table for a difference no caller reads.
    /** The Control modifier key (left or right). */
    Ctrl,
    /** The Shift modifier key (left or right). */
    Shift,
    /** The Alt modifier key (left or right). */
    Alt,
    /** The Meta modifier key (Command on macOS, Windows key on Windows). */
    Meta,

    // Navigation and activation — what a focus ring, a menu and a dialog need.
    /** The Tab navigation key. */
    Tab,
    /** The Enter or Return key. */
    Enter,
    /** The Backspace key. */
    Backspace,
    /** The forward Delete key. */
    Delete,
    /** The Up Arrow navigation key. */
    ArrowUp,
    /** The Down Arrow navigation key. */
    ArrowDown,
    /** The Left Arrow navigation key. */
    ArrowLeft,
    /** The Right Arrow navigation key. */
    ArrowRight,
    /** The Home navigation key. */
    Home,
    /** The End navigation key. */
    End,
    /** The Page Up navigation key. */
    PageUp,
    /** The Page Down navigation key. */
    PageDown,

    // The rest of the letters. A shortcut needs the key's identity: `typedText` reporting that "s"
    // was produced is not the same as knowing S went down while Ctrl was held.
    /** The letter key 'B'. */
    B,
    /** The letter key 'C'. */
    C,
    /** The letter key 'E'. */
    E,
    /** The letter key 'F'. */
    F,
    /** The letter key 'G'. */
    G,
    /** The letter key 'H'. */
    H,
    /** The letter key 'I'. */
    I,
    /** The letter key 'J'. */
    J,
    /** The letter key 'K'. */
    K,
    /** The letter key 'L'. */
    L,
    /** The letter key 'M'. */
    M,
    /** The letter key 'N'. */
    N,
    /** The letter key 'O'. */
    O,
    /** The letter key 'P'. */
    P,
    /** The letter key 'Q'. */
    Q,
    /** The letter key 'R'. */
    R,
    /** The letter key 'T'. */
    T,
    /** The letter key 'U'. */
    U,
    /** The letter key 'V'. */
    V,
    /** The letter key 'X'. */
    X,
    /** The letter key 'Y'. */
    Y,
    /** The letter key 'Z'. */
    Z,

    /** The digit key '0'. */
    Digit0,
    /** The digit key '1'. */
    Digit1,
    /** The digit key '2'. */
    Digit2,
    /** The digit key '3'. */
    Digit3,
    /** The digit key '4'. */
    Digit4,
    /** The digit key '5'. */
    Digit5,
    /** The digit key '6'. */
    Digit6,
    /** The digit key '7'. */
    Digit7,
    /** The digit key '8'. */
    Digit8,
    /** The digit key '9'. */
    Digit9,
}

/**
 * Discrete text-editing commands.
 */
enum class TextEditAction {
    /** Deletes the character before the cursor. */
    Backspace,

    /** Deletes the character after the cursor. */
    Delete,

    /** Inserts a newline or commits the text field. */
    Enter,

    /** Moves the cursor one character to the left. */
    ArrowLeft,

    /** Moves the cursor one character to the right. */
    ArrowRight,

    /** Moves the cursor one line up. */
    ArrowUp,

    /** Moves the cursor one line down. */
    ArrowDown,

    /** Moves the cursor to the beginning of the line. */
    Home,

    /** Moves the cursor to the end of the line. */
    End,

    /** Selects the whole text: Ctrl+A, or Cmd+A on Apple platforms. */
    SelectAll,
}

/**
 * A clipboard command that needs the focused field's answer.
 *
 * Copy and cut are requests: a platform cannot know the selection, and a password field refuses
 * both, so the UI answers through [Input.clipboardWrite] and the platform writes what comes back.
 * Paste has no entry: the platform reads its own clipboard and delivers the text through
 * [Input.pushTypedText], where it follows the same focus and single-line rules as typing.
 */
enum class ClipboardCommand {
    /** Puts the focused field's selection on the clipboard. */
    Copy,

    /** Puts the focused field's selection on the clipboard and removes it from the field. */
    Cut,
}

/**
 * Which pointer button an event or held state belongs to.
 *
 * Modelled as a set on the snapshot rather than a boolean per button, the same way keys are: the
 * held-set already answers "is Middle down", and adding a `middlePointerDown` beside
 * `secondaryPointerDown` would need a third and a fourth the day back/forward matter.
 *
 * [Primary] and [Secondary] keep their dedicated snapshot fields for compatibility -- every
 * existing bridge and consumer reads those -- and are also members of the sets.
 */
enum class PointerButton {
    /** Primary pointer button (typically left mouse button or primary touch). */
    Primary,

    /** Secondary pointer button (typically right mouse button). */
    Secondary,

    /** Middle mouse button or wheel click. */
    Middle,

    /** Auxiliary back button on extended mice. */
    Back,

    /** Auxiliary forward button on extended mice. */
    Forward,
}

/**
 * What sent a frame's scroll, where the platform can tell: a trackpad scrolls in fine, continuous
 * deltas and is usually meant to pan, a wheel in whole notches and is usually meant to zoom.
 *
 * Only desktop macOS reports it today (`NSEvent.hasPreciseScrollingDeltas`); elsewhere a scroll is
 * [Unknown] and a consumer that cares falls back to its own guess.
 */
enum class ScrollSource {
    /** No scroll this frame, or a platform that cannot tell. */
    Unknown,

    /** A notched mouse wheel. */
    Wheel,

    /** A trackpad, or another touch surface such as a Magic Mouse. */
    Trackpad,
}

/**
 * Immutable capture of the hardware state for a single frame.
 *
 * [keysDown] is level-triggered (held), [keysPressed]/[keysReleased] are the edges against the
 * previous frame. The edges live here, computed once in [Input.updateSnapshot], because every
 * consumer that hand-rolled its own `lastKeysDown` diff had to remember to refresh that copy
 * even on the frames it early-returned -- forget it once and a keypress made over a UI widget
 * replays as "just pressed" the moment the UI lets go.
 *
 * @property pointerX Current horizontal coordinate of the primary pointer in screen pixels.
 * @property pointerY Current vertical coordinate of the primary pointer in screen pixels.
 * @property pointerDown True if the primary pointer is currently pressed down.
 * @property scrollDeltaX Horizontal scroll delta accumulated during this frame.
 * @property scrollDeltaY Vertical scroll delta accumulated during this frame.
 * @property keysDown Set of all physical keys currently held down.
 * @property keysPressed Set of physical keys pressed down during this frame.
 * @property keysReleased Set of physical keys released during this frame.
 * @property typedText Unicode text characters typed during this frame.
 * @property editActions High-level text navigation and editing actions triggered during this frame.
 * @property secondaryPointerDown True if the secondary pointer (e.g. right mouse button) is currently pressed down.
 * @property pointerPressed True if a primary pointer press edge occurred during this frame.
 * @property pointerReleased True if a primary pointer release edge occurred during this frame.
 * @property imeComposition Active uncommitted IME pre-edit composition text, or `null` if none.
 * @property imeCommit Finalized text string committed by an IME during this frame, or `null` if none.
 * @property buttonsDown Set of all pointer buttons currently held down.
 * @property buttonsPressed Set of pointer buttons pressed down during this frame.
 * @property buttonsReleased Set of pointer buttons released during this frame.
 * @property scrollSource Hardware source driving scroll deltas for this frame.
 * @property clipboardCommands Copy and cut requests made during this frame, in order.
 */
data class InputSnapshot(
    val pointerX: Float,
    val pointerY: Float,
    val pointerDown: Boolean,
    val scrollDeltaX: Float,
    val scrollDeltaY: Float,
    val keysDown: Set<Key>,
    /** Went down this frame (in [keysDown] now, absent from the previous frame's). */
    val keysPressed: Set<Key>,
    /** Went up this frame (in the previous frame's [keysDown], absent now). */
    val keysReleased: Set<Key>,
    val typedText: String,
    val editActions: List<TextEditAction>,
    val secondaryPointerDown: Boolean = false,
    /** A press happened this frame, even if a matching release also happened before this
     * snapshot was taken. `pointerDown` alone can miss a sub-frame down+up pair -- it only
     * reflects the latest event, not whether one occurred. See [Input.setPointer]. */
    val pointerPressed: Boolean = false,
    /** A release happened this frame, same caveat as [pointerPressed]. */
    val pointerReleased: Boolean = false,
    val imeComposition: ImeComposition? = null,
    val imeCommit: String? = null,
    /** Every pointer button currently held. */
    val buttonsDown: Set<PointerButton> = emptySet(),
    /** Went down this frame (in [buttonsDown] now, absent from the previous frame's). */
    val buttonsPressed: Set<PointerButton> = emptySet(),
    /** Went up this frame (in the previous frame's [buttonsDown], absent now). */
    val buttonsReleased: Set<PointerButton> = emptySet(),
    /** What sent [scrollDeltaX]/[scrollDeltaY], when the platform can tell. */
    val scrollSource: ScrollSource = ScrollSource.Unknown,
    val clipboardCommands: List<ClipboardCommand> = emptyList(),
) {
    /** True if the specified pointer [button] is currently held down. */
    fun isDown(button: PointerButton): Boolean = button in buttonsDown

    /** True if the specified pointer [button] was pressed down during this frame. */
    fun wasPressed(button: PointerButton): Boolean = button in buttonsPressed

    /** True if the specified pointer [button] was released during this frame. */
    fun wasReleased(button: PointerButton): Boolean = button in buttonsReleased

    /** True if the specified [k] key is currently held down. */
    fun isDown(k: Key): Boolean = k in keysDown

    /** True if the specified [k] key was pressed down during this frame. */
    fun wasPressed(k: Key): Boolean = k in keysPressed
}

/**
 * Accumulator for polled input state.
 *
 * One instance per game session. Decoupled from global state.
 */
class Input {
    private val keysDown = mutableSetOf<Key>()
    private val typedText = StringBuilder()
    private val pendingEditActions = mutableListOf<TextEditAction>()
    private val pendingClipboardCommands = mutableListOf<ClipboardCommand>()
    private var imeComposition: ImeComposition? = null
    private var pendingImeCommit: String? = null

    /** True if the primary pointer is currently pressed down. */
    var pointerDown: Boolean = false
        private set

    /** Current horizontal coordinate of the primary pointer in pixels. */
    var pointerX: Float = 0f
        private set

    /** Current vertical coordinate of the primary pointer in pixels. */
    var pointerY: Float = 0f
        private set

    /** Horizontal scroll delta accumulated since the last snapshot. */
    var scrollDeltaX: Float = 0f

    /** Vertical scroll delta accumulated since the last snapshot. */
    var scrollDeltaY: Float = 0f

    /** Set by a bridge that can tell a trackpad from a wheel; cleared each snapshot like the deltas. */
    var scrollSource: ScrollSource = ScrollSource.Unknown

    // Accumulated (OR'd), not overwritten, so a down+up pair landing within one frame
    // interval -- a fast tap, a trackpad tap, any synthetic/automation click -- still
    // latches both edges instead of the second event silently erasing the first. Cleared
    // in updateSnapshot(), same lifecycle as pendingEditActions/typedText below.
    private var pendingPointerPressed = false
    private var pendingPointerReleased = false

    /** Set by the UI pass to signal focus to platform bridges (e.g. soft keyboard). */
    var textInputFocused: Boolean = false

    /**
     * Set by the UI pass when the focused field masks its text, so a platform bridge can tell the
     * IME it is editing a password and the keyboard neither learns nor suggests what is typed.
     *
     * Meaningful only while [textInputFocused] is true. A bridge whose platform has no such hint
     * ignores it; the field still masks what it draws either way.
     */
    var textInputPassword: Boolean = false

    /**
     * Text the UI answered a [ClipboardCommand] with, waiting for the platform bridge to put it on
     * the system clipboard. Set by the UI pass; a bridge takes it with [takeClipboardWrite].
     */
    var clipboardWrite: String? = null

    /** Returns the pending [clipboardWrite] and clears it, so each answer is written once. */
    fun takeClipboardWrite(): String? = clipboardWrite.also { clipboardWrite = null }

    /** The stable hardware state for the current frame. Updated via [updateSnapshot]. */
    var currentSnapshot: InputSnapshot = InputSnapshot(
        pointerX = -1f,
        pointerY = -1f,
        pointerDown = false,
        scrollDeltaX = 0f,
        scrollDeltaY = 0f,
        keysDown = emptySet(),
        keysPressed = emptySet(),
        keysReleased = emptySet(),
        typedText = "",
        editActions = emptyList(),
    )
        private set

    /** Captures the current state into an immutable snapshot and prepares the
     * accumulator for the next frame. */
    fun updateSnapshot(): InputSnapshot {
        val previousKeysDown = currentSnapshot.keysDown
        val previousButtonsDown = currentSnapshot.buttonsDown
        val heldKeys = keysDown.toSet()
        currentSnapshot = InputSnapshot(
            pointerX = pointerX,
            pointerY = pointerY,
            pointerDown = pointerDown,
            scrollDeltaX = scrollDeltaX,
            scrollDeltaY = scrollDeltaY,
            keysDown = heldKeys,
            keysPressed = heldKeys - previousKeysDown,
            keysReleased = previousKeysDown - heldKeys,
            typedText = typedText.toString(),
            imeComposition = imeComposition,
            imeCommit = pendingImeCommit,
            editActions = pendingEditActions.toList(),
            secondaryPointerDown = secondaryPointerDown,
            pointerPressed = pendingPointerPressed,
            pointerReleased = pendingPointerReleased,
            buttonsDown = heldButtons.toSet(),
            buttonsPressed = heldButtons - previousButtonsDown,
            buttonsReleased = previousButtonsDown - heldButtons,
            scrollSource = scrollSource,
            // The shared empty list on the frames without one, which is nearly all of them.
            clipboardCommands = pendingClipboardCommands.takeIf { it.isNotEmpty() }?.toList() ?: emptyList(),
        )
        // Clear transient buffers
        scrollDeltaX = 0f
        scrollDeltaY = 0f
        scrollSource = ScrollSource.Unknown
        typedText.clear()
        pendingEditActions.clear()
        pendingClipboardCommands.clear()
        pendingImeCommit = null
        pendingPointerPressed = false
        pendingPointerReleased = false
        return currentSnapshot
    }

    /** Legacy support or internal use. Prefer [updateSnapshot]. */
    fun snapshot(): InputSnapshot = updateSnapshot()

    /** Checks whether the specified [key] is currently held down. */
    fun isKeyDown(key: Key): Boolean = keysDown.contains(key)

    /** Appends typed character [text] to the current frame accumulator. */
    fun pushTypedText(text: String) {
        typedText.append(text)
    }

    /** Updates visible IME pre-edit text without adding it to [typedText]. */
    fun setImeComposition(composition: ImeComposition?) {
        imeComposition = composition
    }

    /** Finalizes IME text once and clears the persistent pre-edit range. */
    fun commitImeText(text: String) {
        imeComposition = null
        pendingImeCommit = text
    }

    /** Appends a text [action] command to the current frame accumulator. */
    fun pushEditAction(action: TextEditAction) {
        pendingEditActions.add(action)
    }

    /** Appends a clipboard [command] for the focused field to answer this frame. */
    fun pushClipboardCommand(command: ClipboardCommand) {
        pendingClipboardCommands.add(command)
    }

    /** Updates the held state of the specified physical [key]. */
    fun setKeyDown(key: Key, down: Boolean) {
        if (down) keysDown.add(key) else keysDown.remove(key)
    }

    private val heldButtons = mutableSetOf<PointerButton>()

    /** True if the secondary pointer (right mouse button) is currently pressed down. */
    var secondaryPointerDown: Boolean = false
        private set

    /** Updates the pressed state of the secondary pointer. */
    fun setSecondaryPointer(down: Boolean) {
        secondaryPointerDown = down
        setButton(PointerButton.Secondary, down)
    }

    /** Updates the pressed state and pixel position ([x], [y]) of the primary pointer. */
    fun setPointer(down: Boolean, x: Float, y: Float) {
        if (down && !pointerDown) pendingPointerPressed = true
        if (!down && pointerDown) pendingPointerReleased = true
        pointerDown = down
        pointerX = x
        pointerY = y
        setButton(PointerButton.Primary, down)
    }

    /**
     * Holds or releases one button.
     *
     * The dedicated `pointerDown`/`secondaryPointerDown` fields are kept in step by
     * [setPointer]/[setSecondaryPointer] rather than derived from this set, so a bridge that only
     * knows those two keeps working unchanged and one that knows more can call this directly.
     */
    fun setButton(button: PointerButton, down: Boolean) {
        if (down) heldButtons.add(button) else heldButtons.remove(button)
    }

    /** Clears all held physical keys from the accumulator. */
    fun clearKeys() {
        keysDown.clear()
    }
}
