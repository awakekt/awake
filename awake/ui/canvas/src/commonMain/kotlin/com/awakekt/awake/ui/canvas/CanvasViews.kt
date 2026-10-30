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
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.core.color.Color
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
    val mask = settings.mask.shape(element.style.cornerRadius ?: 0f)
    Box(modifier) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                val picture = if (camera.isNotEmpty()) tree.resolver.texture(element, camera, width, height) else null
                if (picture != null) {
                    masked(mask, Rectangle(0f, 0f, width.toFloat(), height.toFloat())) {
                        if (!settings.transparent) drawRect(0f, 0f, width.toFloat(), height.toFloat(), Color.Black)
                        drawTexture(picture, premultiplied = true)
                    }
                }
            },
        )
        Children(element, children, tree)
    }
}
