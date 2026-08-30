/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.core.input.InputSnapshot
import kotlin.math.roundToInt

/**
 * This frame's input, in the shape the compose host takes.
 *
 * A field copy, not a translation: `FrameInput` takes `core.input`'s own `TextEditAction`, so the
 * mirror enum `ui-core` needed -- `UiTextEditAction`, plus a nine-branch `when` to convert -- has
 * nothing to do here.
 *
 * Pointer coordinates round to whole pixels because hit-testing is integer. `ui-core` carried them
 * as floats and compared against float bounds, which put a hit exactly on a boundary on either side
 * depending on rounding no one had chosen.
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
    scrollDeltaY = scrollDeltaY,
    typedText = typedText,
    imeComposition = imeComposition,
    imeCommit = imeCommit,
    editActions = editActions,
    deltaSeconds = deltaSeconds,
)
