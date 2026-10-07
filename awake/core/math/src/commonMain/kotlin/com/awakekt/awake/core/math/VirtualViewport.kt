/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import com.awakekt.awake.core.math2d.Vec2
import kotlin.math.max
import kotlin.math.min

/** How an authored virtual rectangle adapts to a drawable rectangle. */
enum class ViewportScaling {
    /** Preserve the entire virtual rectangle, centering it between bars. */
    Fit,

    /** Show at least the virtual rectangle, extending the world along the longer axis. */
    Extend,

    /** Fill the target with uniform scale, cropping the virtual rectangle at its edges. */
    Fill,

    /** Show the entire virtual rectangle with independent horizontal and vertical scales. */
    Stretch,

    /** One world unit per framebuffer pixel; virtual dimensions are ignored. */
    Screen,
}

/**
 * An immutable orthographic viewport policy, independent of cameras, scenes and graphics APIs.
 * Dimensions use world units, not logical UI pixels. [ViewportScaling.Screen] uses physical pixels.
 *
 * @property width Authored virtual width in world units.
 * @property height Authored virtual height in world units.
 * @property scaling How the virtual rectangle adapts to its target.
 */
data class VirtualViewport(val width: Float, val height: Float, val scaling: ViewportScaling = ViewportScaling.Fit) {
    init {
        require(width.isFinite() && width > 0f && height.isFinite() && height > 0f) {
            "Virtual viewport dimensions must be positive and finite."
        }
    }

    /** Resolves this policy into caller-owned [out], without allocating. Target origin is top-left. */
    @Suppress("LongParameterList") // A target rectangle and a caller-owned output are required.
    fun resolve(targetWidth: Float, targetHeight: Float, out: ViewportLayout, targetX: Float = 0f, targetY: Float = 0f) {
        out.setTarget(targetWidth, targetHeight, targetX, targetY)
        if (!out.isValid) return
        val scaleX = targetWidth / width
        val scaleY = targetHeight / height
        when (scaling) {
            ViewportScaling.Fit -> {
                val scale = min(scaleX, scaleY)
                out.width = min(targetWidth, width * scale)
                out.height = min(targetHeight, height * scale)
                out.x += (targetWidth - out.width) / 2f
                out.y += (targetHeight - out.height) / 2f
                out.worldWidth = width
                out.worldHeight = height
            }
            ViewportScaling.Extend, ViewportScaling.Fill -> {
                val scale = if (scaling == ViewportScaling.Extend) min(scaleX, scaleY) else max(scaleX, scaleY)
                // Crop through projection, never through a negative/out-of-bounds GPU viewport.
                out.worldWidth = targetWidth / scale
                out.worldHeight = targetHeight / scale
            }
            ViewportScaling.Stretch -> {
                out.worldWidth = width
                out.worldHeight = height
            }
            ViewportScaling.Screen -> {
                out.worldWidth = targetWidth
                out.worldHeight = targetHeight
            }
        }
        out.isValid = out.worldWidth.isFinite() &&
            out.worldWidth > 0f &&
            out.worldHeight.isFinite() &&
            out.worldHeight > 0f &&
            out.width > 0f &&
            out.height > 0f
    }
}

/**
 * Reusable result of [VirtualViewport.resolve]. Pixel bounds also serve as the scene scissor.
 * World coordinates here are relative to the camera center, with positive Y up.
 * A zero, negative or non-finite target invalidates the result, including its mappings.
 */
class ViewportLayout {
    /** Whether this layout describes a drawable target. */
    var isValid: Boolean = false
        internal set

    /** Left pixel edge in the target's coordinate space. */
    var x: Float = 0f
        internal set

    /** Top pixel edge in the target's coordinate space. */
    var y: Float = 0f
        internal set

    /** Drawable width in physical pixels. */
    var width: Float = 0f
        internal set

    /** Drawable height in physical pixels. */
    var height: Float = 0f
        internal set

    /** Visible horizontal extent in world units. */
    var worldWidth: Float = 0f
        internal set

    /** Visible vertical extent in world units. */
    var worldHeight: Float = 0f
        internal set

    /** Projection aspect, which differs from the pixel aspect in Stretch mode. */
    val aspect: Float get() = worldWidth / worldHeight

    /** Sets explicit pixel bounds and visible world extents for a caller-defined projection. */
    @Suppress("LongParameterList") // Pixel rectangle and world size are independent.
    fun set(width: Float, height: Float, worldWidth: Float, worldHeight: Float, x: Float = 0f, y: Float = 0f) {
        setTarget(width, height, x, y)
        this.worldWidth = worldWidth
        this.worldHeight = worldHeight
        isValid = isValid && worldWidth.isFinite() && worldWidth > 0f && worldHeight.isFinite() && worldHeight > 0f
    }

    /** Whether a pixel is inside the drawable bounds; bars and right/bottom edges are excluded. */
    fun contains(pixelX: Float, pixelY: Float): Boolean = isValid &&
        pixelX >= x &&
        pixelY >= y &&
        pixelX < x + width &&
        pixelY < y + height

    /** Maps a drawable pixel to camera-relative world units; leaves [out] unchanged outside. */
    fun screenToWorld(pixelX: Float, pixelY: Float, out: Vec2): Boolean {
        if (!contains(pixelX, pixelY)) return false
        out.x = ((pixelX - x) / width - 0.5f) * worldWidth
        out.y = (0.5f - (pixelY - y) / height) * worldHeight
        return true
    }

    /** Maps camera-relative world units to pixels, including positions outside the drawable bounds. */
    fun worldToScreen(worldX: Float, worldY: Float, out: Vec2): Boolean {
        if (!isValid || !worldX.isFinite() || !worldY.isFinite()) return false
        out.x = x + (worldX / worldWidth + 0.5f) * width
        out.y = y + (0.5f - worldY / worldHeight) * height
        return true
    }

    internal fun setTarget(targetWidth: Float, targetHeight: Float, targetX: Float, targetY: Float) {
        x = targetX
        y = targetY
        width = targetWidth
        height = targetHeight
        worldWidth = 0f
        worldHeight = 0f
        isValid = targetWidth.isFinite() &&
            targetWidth > 0f &&
            targetHeight.isFinite() &&
            targetHeight > 0f &&
            targetX.isFinite() &&
            targetY.isFinite()
    }
}
