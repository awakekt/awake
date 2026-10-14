/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerModifiers
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.input.ClipboardCommand
import com.awakekt.awake.core.input.ImeComposition
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.core.input.TextEditAction

/**
 * What the host knows at the start of a frame.
 *
 * Pointer state is **level**, not events: `pointerDown` is "the button is down right now", the shape
 * every windowing toolkit reports and the shape `ui-core`'s `UiInputState` already uses. [ComposeHost]
 * turns it into press/release/move, so a host never has to remember the previous frame itself.
 */
data class FrameInput(
    /** Width of the viewport in pixels, which the root is laid out at. */
    val viewportWidth: Int,
    /** Height of the viewport in pixels, which the root is laid out at. */
    val viewportHeight: Int,
    /** Pointer x in viewport pixels, or [UNKNOWN_POINTER] when it is not over the window. */
    val pointerX: Int = UNKNOWN_POINTER,
    /** Pointer y in viewport pixels, or [UNKNOWN_POINTER] when it is not over the window. */
    val pointerY: Int = UNKNOWN_POINTER,
    /** Whether the primary pointer button is held right now. */
    val pointerDown: Boolean = false,
    /** A press occurred during this frame, including a press/release pair between host frames. */
    val pointerPressed: Boolean = false,
    /** A release occurred during this frame, including a press/release pair between host frames. */
    val pointerReleased: Boolean = false,
    /** The secondary (right) button went down this frame. What opens a context menu. */
    val secondaryPointerPressed: Boolean = false,
    /** Active touch contacts for this frame. Pointer id zero remains reserved for the mouse fields above. */
    val pointers: List<PointerFrame> = emptyList(),
    /**
     * Modifier keys held this frame, attached to every pointer event dispatched from it.
     *
     * Separate from [keyEvents], which reports key *transitions*: a shift-click involves no key
     * transition at all, since Shift went down on an earlier frame and is merely still held.
     */
    val pointerModifiers: PointerModifiers = PointerModifiers.None,
    /** Vertical wheel movement this frame. A non-zero value is dispatched as a wheel event at the pointer. */
    val scrollDeltaY: Float = 0f,
    /** Characters produced this frame. Separate from [editActions] because a keyboard reports them
     * separately -- folding the two loses the difference between typing "\b" and pressing the key. */
    val typedText: String = "",
    /** Current uncommitted IME text. Unlike [typedText], it must not mutate the committed value. */
    val imeComposition: ImeComposition? = null,
    /** Text the IME finalized this frame; an empty string is a valid commit. */
    val imeCommit: String? = null,
    /** Editing commands such as backspace or cursor movement for the focused text field, in order. */
    val editActions: List<TextEditAction> = emptyList(),
    /**
     * Key transitions this frame.
     *
     * A third channel beside [typedText] and [editActions] rather than a replacement for either.
     * They answer different questions: "S went down while Ctrl was held" produces no character at
     * all, and a composed "é" arrives as a character with no single key behind it. Folding them
     * loses both.
     */
    val keyEvents: List<KeyEvent> = emptyList(),
    /** Seconds since the previous frame. It drives [FrameClock] and long-press timing. */
    val deltaSeconds: Float = 1f / 60f,
    /** Copy and cut requests for the focused field, answered in [PlatformEffects.clipboardText]. */
    val clipboardCommands: List<ClipboardCommand> = emptyList(),
) {
    /** Sentinel values for [FrameInput]. */
    companion object {
        /** No pointer on screen -- a touch device between taps, or a window without focus. */
        const val UNKNOWN_POINTER: Int = Int.MIN_VALUE
    }
}

/**
 * One touch contact reported by a platform adapter. [pointerId] remains stable for its gesture.
 *
 * @property pointerId Identifier of the contact. Zero is reserved for the mouse.
 * @property x Horizontal position in viewport pixels.
 * @property y Vertical position in viewport pixels.
 * @property down Whether the contact is touching the surface right now.
 * @property pressed Whether the contact went down during this frame, including a down and up pair between
 * host frames.
 * @property released Whether the contact came up during this frame, including a down and up pair between
 * host frames.
 */
data class PointerFrame(
    val pointerId: Long,
    val x: Int,
    val y: Int,
    val down: Boolean,
    val pressed: Boolean = false,
    val released: Boolean = false,
)

/**
 * What the UI claims of this frame's input, so gameplay can stand down.
 *
 * [isTextInputFocused] is separate from [isCaptured] on purpose: `ui-core` gated gameplay on capture
 * alone, and typing W/A/S/D into a focused field both inserted the letters and walked the player.
 */
data class InputOwnership(
    val isCaptured: Boolean = false,
    /** Something under the pointer could still take a wheel scroll. False at a scroller's end. */
    val isOverScrollable: Boolean = false,
    /** A node consumed this frame's wheel event. */
    val isScrollConsumed: Boolean = false,
    val isTextInputFocused: Boolean = false,
    /** A modal layer is open, so a click anywhere belongs to the UI -- backdrop included. */
    val isModalOpen: Boolean = false,
    /**
     * Whether every focused [com.awakekt.awake.compose.ui.node.TextInputNode] spans one line.
     *
     * Only meaningful with [isTextInputFocused]. Drives [blocksGameplayKey]: a single-line field has
     * no use for [Key.ArrowUp]/[Key.ArrowDown], so they stay readable by gameplay (a chat line
     * stepping through sent history); a multi-line field moves its caret with them, so they stay
     * taken. True when nothing is focused, matching [TextInputNode][com.awakekt.awake.compose.ui.node.TextInputNode]'s own default.
     */
    val isTextInputSingleLine: Boolean = true,
    /**
     * Keys the focused text input declared it does not use, so [blocksGameplayKey] lets them through
     * even though they would otherwise be taken -- the union of every focused
     * [com.awakekt.awake.compose.ui.node.TextInputNode.passThroughKeys]. Empty when nothing is focused.
     */
    val textInputPassThroughKeys: Set<Key> = emptySet(),
)

/**
 * Gameplay must ignore keys when the UI owns the pointer, is taking text, or a modal is open.
 *
 * While [isTextInputFocused] this is `true` even though a focused field only takes *some* keys --
 * see [blocksGameplayKey] for the per-key answer. Kept, rather than narrowed, because a caller that
 * only wants "is the keyboard partly spoken for" (clearing a held movement intent, say) still needs
 * one cheap boolean.
 */
val InputOwnership.blocksGameplayKeys: Boolean get() = isCaptured || isTextInputFocused || isModalOpen

/**
 * Keys a focused text input of [isTextInputSingleLine] handles itself, before
 * [InputOwnership.textInputPassThroughKeys] subtracts anything a field declared it does not want.
 *
 * Matches what [com.awakekt.awake.compose.foundation.text.BasicTextField] actually routes to typing
 * (`Key` identity, not [TextEditAction]) and to [TextEditAction] through `ComposeHost.dispatchText`:
 * every letter and digit and [Key.Space] produce typed text; [Key.Backspace], [Key.Delete],
 * [Key.ArrowLeft], [Key.ArrowRight], [Key.Home], [Key.End] and [Key.Enter] are mapped
 * [TextEditAction]s; [Key.Escape] and [Key.Tab] are not field behavior but are reserved for the UI
 * (dismissing a modal, moving focus) while a field is focused. [Key.ArrowUp]/[Key.ArrowDown] move the
 * caret only in a multi-line field, so they are added only there. [Key.PageUp]/[Key.PageDown] are
 * left out on purpose: no [TextEditAction] exists for them, so even a multi-line field does not yet
 * handle them, and `scene:controls`' fly camera already reads them -- blocking them on a guess would
 * take that away with nothing to show for it.
 */
private fun textInputUsedKeys(singleLine: Boolean): Set<Key> =
    if (singleLine) SINGLE_LINE_TEXT_INPUT_KEYS else MULTILINE_TEXT_INPUT_KEYS

private val PRINTABLE_KEYS: Set<Key> = setOf(
    Key.Space,
    Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
    Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
    Key.Digit0, Key.Digit1, Key.Digit2, Key.Digit3, Key.Digit4,
    Key.Digit5, Key.Digit6, Key.Digit7, Key.Digit8, Key.Digit9,
)

// Modifier chords such as Ctrl+C need no entry of their own: the letter they combine with (C, V, X,
// A, Z) is already in PRINTABLE_KEYS, so the chord is taken regardless of Ctrl being held.
private val SINGLE_LINE_TEXT_INPUT_KEYS: Set<Key> = PRINTABLE_KEYS + setOf(
    Key.Backspace, Key.Delete, Key.ArrowLeft, Key.ArrowRight, Key.Home, Key.End,
    Key.Enter, Key.Escape, Key.Tab,
)

private val MULTILINE_TEXT_INPUT_KEYS: Set<Key> = SINGLE_LINE_TEXT_INPUT_KEYS + setOf(Key.ArrowUp, Key.ArrowDown)

/**
 * Whether gameplay must ignore [key] specifically this frame.
 *
 * The per-key refinement of [blocksGameplayKeys]: pointer capture and a modal still take every key,
 * but a focused text field takes only the keys it uses (see [textInputUsedKeys]), minus whatever it
 * declared through [InputOwnership.textInputPassThroughKeys]. That is what lets a single-line chat
 * field's Up/Down reach a game system for stepping through sent lines while the field still keeps
 * letters and Backspace for itself.
 */
fun InputOwnership.blocksGameplayKey(key: Key): Boolean = when {
    isCaptured || isModalOpen -> true
    !isTextInputFocused -> false
    key in textInputPassThroughKeys -> false
    else -> key in textInputUsedKeys(isTextInputSingleLine)
}

/** Things only the platform can do, requested rather than performed. */
data class PlatformEffects(
    /** A text field holds focus, so the platform should raise its soft keyboard. */
    val requestKeyboard: Boolean = false,
    /**
     * The pointer shape the hovered content asked for.
     *
     * A request: the desktop entry point applies it, and a host that ignores it leaves every
     * hover-driven cursor -- resize handles, text fields -- as the default arrow. `ui-core` reached
     * the same conclusion and carries the same field.
     */
    val cursor: PointerCursor = PointerCursor.Default,
    /**
     * The focused field is a password field. Only meaningful with [requestKeyboard]: a platform that
     * can, tells its IME (Android's `TYPE_TEXT_VARIATION_PASSWORD`), so the keyboard neither learns
     * nor suggests what is typed.
     */
    val passwordKeyboard: Boolean = false,
    /**
     * Text this frame's [FrameInput.clipboardCommands] produced, for the platform to put on the
     * system clipboard; `null` when there is nothing to write -- no command, nothing selected, or
     * a password field that refused.
     */
    val clipboardText: String? = null,
)

/**
 * One frame's worth of output.
 *
 * [primitives] is the same `UiDrawPrimitive` list `ui-core` produces, deliberately: a render backend
 * consumes either engine unchanged, and one scene can be run through both and diffed element by
 * element. That diff is Stage 1's exit gate.
 *
 * @property primitives The frame's draw primitives, in paint order.
 * @property graphicsLayers The offscreen paint passes requested by `Modifier.graphicsLayer`, kept apart
 * from [primitives].
 * @property semantics The accessibility tree for the placed layout, as its top-level nodes.
 * @property ownership What the UI claims of this frame's input.
 * @property effects Things the platform is asked to do in response to this frame.
 */
data class FrameOutput(
    val primitives: List<UiDrawPrimitive>,
    val graphicsLayers: List<GraphicsLayerFrame> = emptyList(),
    val semantics: List<SemanticsNode>,
    val ownership: InputOwnership = InputOwnership(),
    val effects: PlatformEffects = PlatformEffects(),
)
