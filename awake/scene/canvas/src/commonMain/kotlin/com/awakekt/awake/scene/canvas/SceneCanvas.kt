/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongParameterList")

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.input.InputDevice
import com.awakekt.awake.core.localization.Strings
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.ui.canvas.CanvasControls
import com.awakekt.awake.ui.canvas.CanvasFollowLimit
import com.awakekt.awake.ui.canvas.CanvasInsets
import com.awakekt.awake.ui.canvas.CanvasOverlay
import com.awakekt.awake.ui.canvas.CanvasScaler
import com.awakekt.awake.ui.canvas.CanvasSlotContent

context(_: Composer)
/**
 * Draws [world]'s visible [CanvasElement]s over whatever is beneath, lowest [CanvasElement.order]
 * first, and those of the same order in hierarchy order: a node before the nodes under it, and an
 * earlier sibling, with everything under it, before a later one. Of two that overlap, the one drawn
 * later is on top and takes the pointer. Each element is tagged `canvas-element-<entity id>` for
 * tests and editor picking.
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
 * @param views What elements with a [com.awakekt.awake.ui.canvas.CanvasViewWidget] show. Without it they draw only their style.
 *   `SceneAppLifecycleRuntime`'s `canvasViews(world)` gives a host that draws the canvas itself the
 *   runtime's own.
 * @param fonts The loaded font files elements name in [CanvasElement.font], by path, as
 *   [loadCanvasFonts] reads them. An element whose file is missing draws in the default font.
 * @param siblingOrder How the children of one node, and the roots, are ordered, for elements of the
 *   same order: an editor whose hierarchy lets siblings be moved passes its own order, so that what
 *   its hierarchy lists later draws on top. Siblings it calls equal, and every sibling when it is
 *   null, are ordered as the world stores their `Transform`s: the order [world] saves them in, and
 *   for a loaded scene the order its document lists them.
 * @param strings The project's text by key, which an element's [CanvasElement.textKey] is looked up
 *   in, in the active locale; a locale switch shows on the next frame. A played project's are its
 *   `strings/` tables, which `SceneAppLifecycleRuntime` passes as its `canvasStrings`. Without it,
 *   a keyed element shows its key.
 * @param safeArea What covers the screen's edges, in dp: a notch, rounded corners, a home
 *   indicator, overscan. Anchored elements on the canvas itself keep inside it, unless their
 *   [CanvasElement.safeArea] is false. An editor previewing a device passes that device's.
 * @param scaler Works the scale out from the canvas's size, in place of [scale]: the scene's own
 *   `canvas_scaler` by default, through [canvasScaler]. Null draws at [scale].
 * @param followLimits The most followers of each group drawn at once: the scene's own
 *   `canvas_follow_limit`s by default, through [canvasFollowLimits].
 * @param dpi The screen's real dots per inch, for a `ConstantPhysicalSize` scaler; 0 when the host
 *   cannot tell, which takes 160 per dp. `SceneAppLifecycleRuntime` passes its platform's.
 * @param controls What the scene's on-screen controls share: the scene's own `canvas_controls` by
 *   default, through [canvasControls]. Null shows touch controls as [showTouchControls] says.
 * @param inputDevice The device the player last used, which `canvas_controls`' `Auto` visibility
 *   shows and hides touch controls by. `SceneAppLifecycleRuntime` passes the one it tracks.
 * @param slotContent The composables a control's custom slot names, by name, each given the
 *   control's live state. `SceneAppLifecycleRuntime` passes its `canvasSlotContent`.
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
    siblingOrder: Comparator<Entity>? = null,
    strings: Strings? = null,
    safeArea: CanvasInsets = CanvasInsets.None,
    scaler: CanvasScaler? = world.canvasScaler(),
    followLimits: List<CanvasFollowLimit> = world.canvasFollowLimits(),
    dpi: Float = 0f,
    controls: CanvasControls? = world.canvasControls(),
    inputDevice: InputDevice? = null,
    slotContent: Map<String, CanvasSlotContent> = emptyMap(),
) {
    // Kept from frame to frame: sorting every element and indexing every named node each frame cost
    // more than drawing a large canvas does.
    // Components changed on an entity, or through its element, since the last frame.
    world.syncCanvasElements()
    val order = remember(world) { CanvasItemOrder(world) }
    val named = remember(world) { CanvasNodeNames(world) }
    named.nextFrame()
    CanvasOverlay(
        if (siblingOrder == null) order.items() else world.canvasItems(siblingOrder),
        modifier,
        showTouchControls,
        images,
        scale,
        SceneCanvasResolver(world, projector, views, bindings, named),
        fonts,
        strings,
        safeArea,
        scaler,
        followLimits,
        dpi,
        controls,
        inputDevice,
        slotContent,
    )
}
