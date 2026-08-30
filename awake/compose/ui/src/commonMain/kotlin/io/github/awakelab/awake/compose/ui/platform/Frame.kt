/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.platform

import io.github.awakelab.awake.compose.ui.input.key.KeyEvent
import io.github.awakelab.awake.compose.ui.input.pointer.PointerModifiers
import io.github.awakelab.awake.compose.ui.semantics.SemanticsNode
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import io.github.awakelab.awake.core.input.PointerCursor
import io.github.awakelab.awake.core.input.ImeComposition
import io.github.awakelab.awake.core.input.TextEditAction

/**
 * What the host knows at the start of a frame.
 *
 * Pointer state is **level**, not events: `pointerDown` is "the button is down right now", the shape
 * every windowing toolkit reports and the shape `ui-core`'s `UiInputState` already uses. [ComposeHost]
 * turns it into press/release/move, so a host never has to remember the previous frame itself.
 */
data class FrameInput(
    val viewportWidth: Int,
    val viewportHeight: Int,
    val pointerX: Int = UNKNOWN_POINTER,
    val pointerY: Int = UNKNOWN_POINTER,
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
    val scrollDeltaY: Float = 0f,
    /** Characters produced this frame. Separate from [editActions] because a keyboard reports them
     * separately -- folding the two loses the difference between typing "\b" and pressing the key. */
    val typedText: String = "",
    /** Current uncommitted IME text. Unlike [typedText], it must not mutate the committed value. */
    val imeComposition: ImeComposition? = null,
    /** Text the IME finalized this frame; an empty string is a valid commit. */
    val imeCommit: String? = null,
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
    val deltaSeconds: Float = 1f / 60f,
) {
    companion object {
        /** No pointer on screen -- a touch device between taps, or a window without focus. */
        const val UNKNOWN_POINTER: Int = Int.MIN_VALUE
    }
}

/** One touch contact reported by a platform adapter. [pointerId] remains stable for its gesture. */
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
    val isOverScrollable: Boolean = false,
    val isScrollConsumed: Boolean = false,
    val isTextInputFocused: Boolean = false,
    /** A modal layer is open, so a click anywhere belongs to the UI -- backdrop included. */
    val isModalOpen: Boolean = false,
)

/** Gameplay must ignore keys when the UI owns the pointer **or** is taking text. */
val InputOwnership.blocksGameplayKeys: Boolean get() = isCaptured || isTextInputFocused

/** Things only the platform can do, requested rather than performed. */
data class PlatformEffects(
    val requestKeyboard: Boolean = false,
    /**
     * The pointer shape the hovered content asked for.
     *
     * A request: the desktop entry point applies it, and a host that ignores it leaves every
     * hover-driven cursor -- resize handles, text fields -- as the default arrow. `ui-core` reached
     * the same conclusion and carries the same field.
     */
    val cursor: PointerCursor = PointerCursor.Default,
)

/**
 * One frame's worth of output.
 *
 * [primitives] is the same `UiDrawPrimitive` list `ui-core` produces, deliberately: a render backend
 * consumes either engine unchanged, and one scene can be run through both and diffed element by
 * element. That diff is Stage 1's exit gate.
 */
data class FrameOutput(
    val primitives: List<UiDrawPrimitive>,
    val graphicsLayers: List<GraphicsLayerFrame> = emptyList(),
    val semantics: List<SemanticsNode>,
    val ownership: InputOwnership = InputOwnership(),
    val effects: PlatformEffects = PlatformEffects(),
)
