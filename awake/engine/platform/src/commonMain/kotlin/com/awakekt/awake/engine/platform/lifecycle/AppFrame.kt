/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.lifecycle

import com.awakekt.awake.core.input.InputSnapshot

/**
 * Everything one frame hands [AppLifecycle.update].
 *
 * Pushed rather than pulled: a game's update never reaches into ambient state for the frame it
 * is already being called about. [input] is the snapshot taken for this frame specifically --
 * the long-lived `Input` accumulator behind it stays an injected service, since platform
 * bridges write into it outside the frame.
 *
 * New per-frame values belong here rather than as extra `update` parameters.
 *
 * @property delta Elapsed time in seconds since the previous frame.
 * @property viewportWidth Framebuffer width in physical pixels.
 * @property viewportHeight Framebuffer height in physical pixels.
 * @property input Input state snapshot captured for this frame.
 * @property density Physical pixels per dp-equivalent display unit.
 */
data class AppFrame(
    val delta: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val input: InputSnapshot,
    val density: Float = 1f,
)
