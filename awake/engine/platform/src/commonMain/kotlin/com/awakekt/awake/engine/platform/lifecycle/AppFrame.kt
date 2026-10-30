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
 * @property safeArea What covers the window's edges, in dp, as its platform reports it: a notch,
 *   rounded corners, system bars drawn over the game. [DisplayInsets.None] where nothing does or the
 *   host cannot tell.
 * @property dpi The screen's real dots per inch, physical pixels to a physical inch, where the
 *   platform can tell; 0 where it cannot, for a reader to fall back on 160 per dp.
 * @property systemGestureInsets Edges where the OS handles system gestures, in dp, independently
 *   of [safeArea]. Layouts can choose to avoid these edges too. None where the host reports none.
 */
data class AppFrame(
    val delta: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val input: InputSnapshot,
    val density: Float = 1f,
    val safeArea: DisplayInsets = DisplayInsets.None,
    val dpi: Float = 0f,
    val systemGestureInsets: DisplayInsets = DisplayInsets.None,
)
