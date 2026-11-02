/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

/**
 * A custom pointer image a frame asks the platform host to display, instead of one of
 * [PointerCursor]'s system shapes.
 *
 * A request, not a call -- the same contract as [PointerCursor]: whatever produced the frame
 * records what it wants, and the platform entry point turns it into a real cursor (a desktop host
 * builds a GLFW cursor from the pixels; a browser host sets a CSS `url(...)` cursor). Nothing here
 * reaches a windowing API, so this type stays dependency-free and shared by both UI engines and the
 * render backends, the way [PointerCursor] is.
 *
 * [rgba] holds `width * height` straight (non-premultiplied) RGBA pixels, one byte per channel,
 * row-major with the **top** row first -- the order GLFW's image cursor and an encoded PNG both
 * want. [hotspotX] and [hotspotY] are the click point, in pixels from the top-left, so `0, 0` is a
 * top-left-anchored pointer.
 *
 * Equality is by identity, not by pixels: a host caches the native cursor it builds for an instance
 * and reuses it while the same instance is requested, so load a cursor once and hold the instance
 * rather than rebuilding it each frame.
 */
class CursorImage(
    rgba: ByteArray,
    /** The image width in pixels. */
    val width: Int,
    /** The image height in pixels. */
    val height: Int,
    val hotspotX: Int = 0,
    val hotspotY: Int = 0,
) {
    /** The pixels, copied on construction so a later change to the caller's array can't alter a cursor already shown. */
    val rgba: ByteArray = rgba.copyOf()

    init {
        require(width > 0 && height > 0) { "cursor size must be positive, was ${width}x$height" }
        require(this.rgba.size == width * height * PIXEL_STRIDE) {
            "rgba must hold width*height*4 bytes (${width * height * PIXEL_STRIDE}), was ${this.rgba.size}"
        }
        require(hotspotX in 0 until width && hotspotY in 0 until height) {
            "hotspot ($hotspotX, $hotspotY) must lie within ${width}x$height"
        }
    }

    override fun toString(): String = "CursorImage(${width}x$height, hotspot=($hotspotX, $hotspotY))"

    /** Constants for [CursorImage]'s pixel layout. */
    companion object {
        /** Bytes per pixel: R, G, B, A. */
        const val PIXEL_STRIDE: Int = 4
    }
}
