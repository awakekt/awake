/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.compose.ui.platform.FrameInput
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
    pointerX = pointerX.roundToInt(),
    pointerY = pointerY.roundToInt(),
    pointerDown = pointerDown,
    pointerPressed = pointerPressed,
    pointerReleased = pointerReleased,
    secondaryPointerPressed = PointerButton.Secondary in buttonsPressed,
    pointerModifiers = pointerModifiers(),
    scrollDeltaY = scrollDeltaY,
    typedText = typedText,
    imeComposition = imeComposition,
    imeCommit = imeCommit,
    editActions = editActions,
    keyEvents = keyEvents(),
    deltaSeconds = deltaSeconds,
)
