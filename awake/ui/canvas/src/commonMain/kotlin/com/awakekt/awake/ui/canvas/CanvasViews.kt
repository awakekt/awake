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
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color

/**
 * A View: what its camera sees, filling its box inside its style's rounded corners at the box's size
 * in pixels, under its children. No picture without a [CanvasResolver] that answers for the camera's
 * name.
 *
 * Opaque, as the camera's picture is on screen: a blended draw lowers the target's alpha even over a
 * solid surface, so the picture goes over black rather than over what is behind the element.
 */
context(_: Composer)
internal fun CameraView(
    element: CanvasElement,
    modifier: Modifier,
    tree: CanvasTree,
    children: List<CanvasItem>,
) {
    val camera = element.camera
    val radius = element.style.cornerRadius ?: 0f
    val box = if (radius > 0f) Modifier.fillMaxSize().clip(RoundedCornerShape(radius.dp)) else Modifier.fillMaxSize()
    Box(modifier) {
        Box(
            box.drawBehind {
                val picture = if (camera.isNotEmpty()) tree.resolver.texture(element, camera, width, height) else null
                if (picture != null) {
                    drawRect(0f, 0f, width.toFloat(), height.toFloat(), Color.Black)
                    drawTexture(picture, premultiplied = true)
                }
            },
        )
        Children(element, children, tree)
    }
}
