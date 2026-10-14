/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.Entity

/**
 * The pictures [CanvasElementKind.View] elements show. The host that draws the scene supplies it and
 * renders every picture it hands out before the frame's UI is drawn. `SceneAppLifecycleRuntime` does
 * for the canvas it draws, and its `canvasViews(world)` gives one to a host that draws `SceneCanvas`
 * itself, rendering and freeing the pictures for it.
 */
fun interface CanvasViews {
    /**
     * A texture `DrawScope.drawTexture` takes, holding what the camera on [camera] sees at [width] by
     * [height] pixels, for [element] to show this frame; null when there is nothing to show, such as no
     * camera on that node.
     */
    fun texture(element: CanvasElement, camera: Entity, width: Int, height: Int): Any?
}

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
