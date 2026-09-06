/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/** Frames per second every [FrameLoop] throttles to. A constant, not config: nothing has
 * needed a second value yet. Give it a real home on `WindowConfig` when something does. */
const val TARGET_FPS = 60

interface FrameLoop {
    /** Runs exactly one frame -- measures the delta since the previous call, invokes
     * [onUpdate], then sleeps out the remainder of the [TARGET_FPS] budget. The caller owns
     * the repeat loop. */
    fun tick(onUpdate: (deltaTime: Double) -> Unit)
}
