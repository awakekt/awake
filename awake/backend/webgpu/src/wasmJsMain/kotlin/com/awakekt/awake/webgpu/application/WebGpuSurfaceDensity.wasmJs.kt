/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.application

import kotlinx.browser.window

/** The browser's own scale factor, which is exactly what the canvas is already sized by. */
actual fun webGpuSurfaceDensity(): Float {
    val scale = window.devicePixelRatio
    return if (scale.isFinite() && scale > 0.0) scale.toFloat() else 1f
}
