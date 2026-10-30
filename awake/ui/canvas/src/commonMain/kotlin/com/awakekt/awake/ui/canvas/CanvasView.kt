/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlinx.serialization.Serializable

/**
 * How a camera picture is composited into a canvas.
 *
 * @property transparent Clears the picture to transparent and omits the scene's sky. Geometry,
 * lighting and fog still render; the panel behind the View shows through uncovered pixels.
 * @property mask The picture's outline. Box uses the style's corner radius; ancestor clips still
 * apply. This masks the picture, not the element's background or its overlay children.
 */
@Serializable
data class CanvasView(val transparent: Boolean = false, val mask: CanvasMask = CanvasMask.Box) {
    /** The historical opaque picture, clipped to its styled box. */
    companion object {
        /** Default View settings. */
        val Default = CanvasView()
    }
}
