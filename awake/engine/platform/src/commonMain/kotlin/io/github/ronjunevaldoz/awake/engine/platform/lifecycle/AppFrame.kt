// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.engine.platform.lifecycle

import io.github.ronjunevaldoz.awake.core.input.InputSnapshot

/**
 * Everything one frame hands [AppLifecycle.update].
 *
 * Pushed rather than pulled: a game's update never reaches into ambient state for the frame it
 * is already being called about. [input] is the snapshot taken for this frame specifically --
 * the long-lived `Input` accumulator behind it stays an injected service, since platform
 * bridges write into it outside the frame.
 *
 * New per-frame values belong here rather than as extra `update` parameters.
 */
data class AppFrame(
    val delta: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val input: InputSnapshot,
)
