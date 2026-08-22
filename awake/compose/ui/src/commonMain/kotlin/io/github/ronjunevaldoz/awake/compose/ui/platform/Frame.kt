// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.platform

import io.github.ronjunevaldoz.awake.compose.ui.semantics.SemanticsNode
import io.github.ronjunevaldoz.awake.compose.ui.text.input.EditCommand
import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive

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
    val scrollDeltaY: Float = 0f,
    /** Characters produced this frame. Separate from [editCommands] because a keyboard reports them
     * separately -- folding the two loses the difference between typing "\b" and pressing the key. */
    val typedText: String = "",
    val editCommands: List<EditCommand> = emptyList(),
    val deltaSeconds: Float = 1f / 60f,
) {
    companion object {
        /** No pointer on screen -- a touch device between taps, or a window without focus. */
        const val UNKNOWN_POINTER: Int = Int.MIN_VALUE
    }
}

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
    val semantics: List<SemanticsNode>,
    val ownership: InputOwnership = InputOwnership(),
    val effects: PlatformEffects = PlatformEffects(),
)
