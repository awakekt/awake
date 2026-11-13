/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

import com.awakekt.awake.compose.ui.input.pointer.PointerModifiers
import com.awakekt.awake.compose.ui.unit.LayoutDirection

/**
 * The plain values [ComposeHost] provides to composition through composition locals, as the last
 * composed pass saw them.
 *
 * None of them is a read-tracked state, so a pass that read one cannot be told it changed. A host
 * that skips an unchanged pass compares them itself: one that differs from the last composed pass
 * means the pass runs. Holds no allocation per frame: the fields are overwritten in place.
 */
internal class CompositionInputs {
    private var composed = false
    private var density = 0f
    private var width = 0
    private var height = 0
    private var insets: WindowInsets = WindowInsets.None
    private var modifiers: PointerModifiers? = null
    private var direction = LayoutDirection.Ltr

    /** Whether any value differs from the one the last [record] saw, or nothing was recorded yet. */
    fun differsFrom(
        density: Float,
        input: FrameInput,
        insets: WindowInsets,
        direction: LayoutDirection,
    ): Boolean = !composed ||
        this.density != density ||
        width != input.viewportWidth ||
        height != input.viewportHeight ||
        this.insets != insets ||
        modifiers != input.pointerModifiers ||
        this.direction != direction

    /** Remembers what the pass that is about to run sees. */
    fun record(density: Float, input: FrameInput, insets: WindowInsets, direction: LayoutDirection) {
        composed = true
        this.density = density
        width = input.viewportWidth
        height = input.viewportHeight
        this.insets = insets
        modifiers = input.pointerModifiers
        this.direction = direction
    }

    /** Forgets everything, so the next [differsFrom] is true. */
    fun reset() {
        composed = false
    }
}
