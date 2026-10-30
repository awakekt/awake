/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.runtime.rememberGroup
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.safeInteriorMargin
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.math2d.Rectangle

/**
 * A View: what its camera sees, filling its box inside its picture mask at the box's size in pixels,
 * under its children. No picture without a [CanvasResolver] that answers for the camera's name.
 *
 * Opaque by default, over the same black backing as before. An explicitly transparent View keeps
 * the render target's premultiplied coverage instead, so its surroundings show through.
 */
context(_: Composer)
internal fun CameraView(
    element: CanvasElement,
    modifier: Modifier,
    tree: CanvasTree,
    children: List<CanvasItem>,
) {
    val camera = element.camera
    val settings = element.view ?: CanvasView.Default
    val radius = element.style.cornerRadius ?: 0f
    val mask = rememberGroup(CameraViewGroup) { remember { PictureMask() } }
    Box(modifier) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                val picture = if (camera.isNotEmpty()) tree.resolver.texture(element, camera, width, height) else null
                if (picture != null) {
                    mask.clip(this, settings.mask, radius) {
                        if (!settings.transparent) drawRect(0f, 0f, width.toFloat(), height.toFloat(), Color.Black)
                        drawTexture(picture, premultiplied = true)
                    }
                }
            },
        )
        Children(element, children, tree)
    }
}

private object CameraViewGroup

/**
 * A View's picture outline, kept across frames: its shape is resolved again only when the mask or
 * corner radius changes, and its path only when the shape, box size or density does.
 */
internal class PictureMask {
    private var mask: CanvasMask? = null
    private var radius = Float.NaN
    private var shape: DrawShape = DrawShape.Rectangle

    // -1 until a path is built for the current shape.
    private var width = -1
    private var height = -1
    private var density = Float.NaN
    private var path: DrawPath? = null
    private var safeInterior: Rectangle? = null

    /** Draws [block] cut to [mask], with [radius] corners, over [scope]'s box: a plain clip for a rectangle. */
    fun clip(scope: DrawScope, mask: CanvasMask, radius: Float, block: () -> Unit) {
        if (mask != this.mask || radius != this.radius) {
            this.mask = mask
            this.radius = radius
            shape = mask.shape(radius)
            width = -1
        }
        if (shape == DrawShape.Rectangle) {
            scope.clipped(block = block)
            return
        }
        if (scope.width != width || scope.height != height || scope.density != density) {
            width = scope.width
            height = scope.height
            density = scope.density
            val bounds = Rectangle(0f, 0f, width.toFloat(), height.toFloat())
            path = shape.toPath(bounds, density = density)
            // Everything inside the corners skips exact path clipping, as a `Modifier.clip` does.
            val inset = shape.safeInteriorMargin(bounds, density)
            safeInterior = if (bounds.width > 2 * inset && bounds.height > 2 * inset) {
                Rectangle(inset, inset, bounds.width - 2 * inset, bounds.height - 2 * inset)
            } else {
                null
            }
        }
        scope.clippedPath(checkNotNull(path), safeInterior, block)
    }
}
