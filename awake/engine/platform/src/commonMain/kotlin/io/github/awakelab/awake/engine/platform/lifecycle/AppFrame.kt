/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.engine.platform.lifecycle

import io.github.awakelab.awake.core.input.InputSnapshot

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
    /** Physical pixels per dp-equivalent unit -- 1f (unscaled) unless the backend knows the
     * real display scale. [viewportWidth]/[viewportHeight] are always physical framebuffer
     * pixels; a UI layer that sizes itself in device-independent units needs this to convert. */
    val density: Float = 1f,
)
