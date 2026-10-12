/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity

/**
 * The pictures [CanvasElementKind.View] elements show. The host that draws the scene supplies it and
 * renders every picture it hands out before the frame's UI is drawn; `SceneAppLifecycleRuntime` does.
 */
fun interface CanvasViews {
    /**
     * A texture `DrawScope.drawTexture` takes, holding what the camera on [camera] sees at [width] by
     * [height] pixels, for [element] to show this frame; null when there is nothing to show, such as no
     * camera on that node.
     */
    fun texture(element: CanvasElement, camera: Entity, width: Int, height: Int): Any?
}
