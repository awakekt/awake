/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongParameterList")

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.ui.canvas.CanvasOverlay

context(_: Composer)
/**
 * Draws [world]'s visible [CanvasElement]s over whatever is beneath, lowest [CanvasElement.order]
 * first. Each element is tagged `canvas-element-<entity id>` for tests and editor picking.
 * [CanvasElement.touchOnly] elements are drawn only when [showTouchControls] is true.
 *
 * An element whose entity has another element above it in the scene hierarchy is drawn inside the
 * nearest one: anchored and offset within its parent's box, after the parent's own content, and
 * hidden when it is. A window and its controls move as one.
 *
 * @param world The ECS world containing canvas entities to render.
 * @param modifier Layout modifier applied to the overlay container.
 * @param showTouchControls Whether touch-only elements should be displayed.
 * @param images The decoded images elements name, by path, as [loadCanvasImages] reads them. An
 *   element whose image is missing draws without it.
 * @param scale Multiplies every element's size, offset, text and image pixels: 2 draws 1x art at
 *   twice its size. Edges land on whole pixels at any scale, so pixel art stays crisp. A scale that
 *   is not above 0 draws at 1.
 * @param projector Where the scene's nodes land in the frame, for elements that
 *   [CanvasElement.follow] one. Without it they are not drawn. `SceneAppLifecycleRuntime`'s
 *   `canvasProjector(world)` gives a host that draws the canvas itself the runtime's own.
 * @param bindings The scene component bindings an element's [CanvasElement.bind] reads fields
 *   through: the scene's own registry's. Null reads through every globally registered one, gathered
 *   only on a frame where an element reads a field.
 * @param views What [CanvasElementKind.View] elements show. Without it they draw only their style.
 *   `SceneAppLifecycleRuntime`'s `canvasViews(world)` gives a host that draws the canvas itself the
 *   runtime's own.
 * @param fonts The loaded font files elements name in [CanvasElement.font], by path, as
 *   [loadCanvasFonts] reads them. An element whose file is missing draws in the default font.
 */
fun SceneCanvas(
    world: World,
    modifier: Modifier = Modifier,
    showTouchControls: Boolean = false,
    images: Map<String, ImageBitmap> = emptyMap(),
    scale: Float = 1f,
    projector: CanvasProjector? = null,
    bindings: List<SceneComponentBinding<*, *>>? = null,
    views: CanvasViews? = null,
    fonts: Map<String, UiFont> = emptyMap(),
) {
    CanvasOverlay(
        world.canvasItems(),
        modifier,
        showTouchControls,
        images,
        scale,
        SceneCanvasResolver(world, projector, views, bindings),
        fonts,
    )
}
