/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * The pixel ratio a browser canvas's buffer is drawn at: [devicePixelRatio], capped at
 * [maxPixelRatio].
 *
 * The same ratio is the UI's density, so a cap shrinks the buffer without changing the size of
 * anything laid out in it: a 400 CSS-pixel panel is 800 buffer pixels at a ratio of 2 and 600 at a
 * cap of 1.5, and covers the same 400 CSS pixels of the page either way.
 *
 * A device ratio that isn't a positive finite number counts as 1, and a cap that is `null`, not
 * positive or not finite is no cap, so the default leaves the buffer exactly as it was.
 */
internal fun canvasPixelRatio(devicePixelRatio: Double, maxPixelRatio: Float?): Double {
    val device = if (devicePixelRatio.isFinite() && devicePixelRatio > 0.0) devicePixelRatio else 1.0
    val cap = maxPixelRatio?.takeIf { it.isFinite() && it > 0f } ?: return device
    return minOf(device, cap.toDouble())
}
