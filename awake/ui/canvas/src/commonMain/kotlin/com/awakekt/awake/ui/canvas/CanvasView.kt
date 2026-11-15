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
 * lighting and fog still render; the panel behind the View shows through uncovered pixels. The
 * coverage survives a multisampled plan's resolve. A View draws its camera's scene pass without the
 * camera's post-processing stack, whose tone mapping and FXAA would overwrite alpha.
 * @property mask The picture's outline. Box uses the style's corner radius; ancestor clips still
 * apply. This masks the picture, not the element's background or its overlay children.
 * @property refresh When the picture is drawn again: every frame, or only when first shown, resized
 * or asked for. A View whose subject changes rarely keeps its last picture and costs nothing between
 * requests.
 */
@Serializable
data class CanvasView(
    val transparent: Boolean = false,
    val mask: CanvasMask = CanvasMask.Box,
    val refresh: CanvasViewRefresh = CanvasViewRefresh.Always,
) {
    /** The constructor as released before [refresh], kept so compiled callers still link. */
    @Deprecated("Use the constructor with refresh.", level = DeprecationLevel.HIDDEN)
    constructor(transparent: Boolean = false, mask: CanvasMask = CanvasMask.Box) : this(transparent, mask, CanvasViewRefresh.Always)

    /** [copy] as released before [refresh]. */
    @Deprecated("Use copy with refresh.", level = DeprecationLevel.HIDDEN)
    fun copy(transparent: Boolean = this.transparent, mask: CanvasMask = this.mask): CanvasView = copy(transparent, mask, refresh)

    /** The historical opaque picture, clipped to its styled box. */
    companion object {
        /** Default View settings. */
        val Default = CanvasView()
    }
}
