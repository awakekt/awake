/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.compose.ui.platform.keyEvents
import com.awakekt.awake.compose.ui.platform.pointerModifiers
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.PointerButton
import kotlin.math.roundToInt

/**
 * Converts an [InputSnapshot] into a [FrameInput] for Compose UI frame composition.
 */
internal fun InputSnapshot.toFrameInput(
    viewportWidth: Int,
    viewportHeight: Int,
    deltaSeconds: Float,
): FrameInput = FrameInput(
    viewportWidth = viewportWidth,
    viewportHeight = viewportHeight,
    // A finger reaches the UI once, as its own pointer below. The primary pointer it also drives
    // is for engine code that reads only that, and would otherwise press the UI a second time.
    pointerX = if (pointerFromTouch) FrameInput.UNKNOWN_POINTER else pointerX.roundToInt(),
    pointerY = if (pointerFromTouch) FrameInput.UNKNOWN_POINTER else pointerY.roundToInt(),
    pointerDown = pointerDown && !pointerFromTouch,
    pointerPressed = pointerPressed && !pointerFromTouch,
    pointerReleased = pointerReleased && !pointerFromTouch,
    // Pointer id 0 is the mouse's, so fingers start at 1. No list at all on the frames without a
    // finger, which on a desktop is all of them.
    pointers = if (touches.isEmpty()) emptyList() else touches.map { PointerFrame(it.id + 1, it.x.roundToInt(), it.y.roundToInt(), it.down, it.pressed, it.released) },
    secondaryPointerPressed = PointerButton.Secondary in buttonsPressed,
    pointerModifiers = pointerModifiers(),
    scrollDeltaY = scrollDeltaY,
    typedText = typedText,
    imeComposition = imeComposition,
    imeCommit = imeCommit,
    editActions = editActions,
    keyEvents = keyEvents(),
    deltaSeconds = deltaSeconds,
    clipboardCommands = clipboardCommands,
)
