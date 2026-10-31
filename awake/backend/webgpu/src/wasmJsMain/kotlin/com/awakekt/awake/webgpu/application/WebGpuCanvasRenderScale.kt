/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.application

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.engine.platform.WindowLifecycle
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.engine.window.findCanvas
import web.html.HTMLCanvasElement

/*
 * The launchWebGpuGame overloads that take a renderScale provider. Overloads, not new defaulted
 * parameters on the existing functions, so the published signatures stay as they were. A game
 * exposes the scale as a graphics setting, for example
 * `launchWebGpuGame(game, plan, renderScale = { settings.renderScale })`.
 *
 * renderScale or maxPixelRatio: renderScale shrinks only the 3D scene, so text and HUD edges stay
 * as sharp as the device can show them; it is the one to put behind a quality setting.
 * maxPixelRatio caps the whole canvas buffer, UI included, and is the hard ceiling on pixels for
 * hosts that must bound memory or fill rate whatever the scene costs. The two compose: the scene
 * renders at renderScale of the capped buffer.
 */

/**
 * Starts the engine [applicationFactory] builds on [canvas], as the overload without [renderScale]
 * does, with the 3D scene rendered at [renderScale] of the canvas buffer's size.
 *
 * [renderScale] is read before every frame and clamped to `(0, 1]` by `RenderScale.coerce`, so a
 * game can change it at run time; the new value takes effect on that frame. Below 1, the scene, its
 * depth and its post-processing render into a smaller target that a bilinear upscale copies to the
 * canvas, and the UI then draws over it at the full buffer resolution. At 1 the frame is the one
 * drawn without a provider: no extra target and no extra pass. `Renderer.surfaceWidth`/
 * `surfaceHeight` report the scaled scene target; `Renderer.outputWidth`/`outputHeight`, pointers
 * and scene viewports stay on the canvas buffer.
 *
 * WebGPU only: the Vulkan backend does not implement `Renderer.renderScale` yet and ignores it.
 *
 * @param canvas The canvas element to render into and to size the surface from.
 * @param cursor The pointer shape the UI asks for this frame, or `null` to leave the canvas's cursor
 * alone.
 * @param maxPixelRatio The most buffer pixels per CSS pixel, or `null` for the device pixel ratio.
 * @param renderScale The fraction of the buffer's size the 3D scene renders at, in `(0, 1]`.
 * @param applicationFactory Builds the engine, called once after the capability check and before
 * the device is created.
 * @throws IllegalStateException If the browser or device does not expose WebGPU.
 */
fun launchWebGpuGame(
    canvas: HTMLCanvasElement,
    cursor: (() -> PointerCursor)? = null,
    maxPixelRatio: () -> Float? = { null },
    renderScale: () -> Float,
    applicationFactory: () -> WebGpuEngine,
) {
    startWebGpuGame(canvas, cursor, maxPixelRatio, renderScale, applicationFactory)
}

/**
 * Starts the engine [applicationFactory] builds on the `<canvas>` with [canvasId], with the 3D scene
 * rendered at [renderScale]. See the canvas overload that takes a render scale.
 */
fun launchWebGpuGame(
    canvasId: String = "awake-canvas",
    cursor: (() -> PointerCursor)? = null,
    maxPixelRatio: () -> Float? = { null },
    renderScale: () -> Float,
    applicationFactory: () -> WebGpuEngine,
) {
    val canvas = findCanvas(canvasId).unsafeCast<HTMLCanvasElement>()
    startWebGpuGame(canvas, cursor, maxPixelRatio, renderScale, applicationFactory)
}

/**
 * Starts [game] on the `<canvas>` with [canvasId], drawn by a [WebGpuEngine] built from [plan], with
 * the 3D scene rendered at [renderScale]. See the canvas overload that takes a render scale.
 */
@Suppress("LongParameterList")
fun launchWebGpuGame(
    game: AwakeAppLifecycle,
    plan: RenderPlan,
    canvasId: String = "awake-canvas",
    cursor: (() -> PointerCursor)? = null,
    maxPixelRatio: () -> Float? = { null },
    renderScale: () -> Float,
) {
    launchWebGpuGame(canvasId, cursor, maxPixelRatio, renderScale) { WebGpuEngine(appLifecycle = game, requestedPlan = plan) }
}

/**
 * Starts [game] on [canvas], drawn by a [WebGpuEngine] built from [plan], with the 3D scene rendered
 * at [renderScale]. See the canvas overload that takes a render scale.
 */
@Suppress("LongParameterList")
fun launchWebGpuGame(
    game: AwakeAppLifecycle,
    plan: RenderPlan,
    canvas: HTMLCanvasElement,
    cursor: (() -> PointerCursor)? = null,
    maxPixelRatio: () -> Float? = { null },
    renderScale: () -> Float,
) {
    startWebGpuGame(canvas, cursor, maxPixelRatio, renderScale) { WebGpuEngine(appLifecycle = game, requestedPlan = plan) }
}

/** [engine], with [renderScale] read and applied to its renderer before each frame it updates. */
internal class RenderScaledLifecycle(
    private val engine: WebGpuEngine,
    private val renderScale: () -> Float,
) : WindowLifecycle by engine {
    override fun update(delta: Float) {
        engine.applyRenderScale(renderScale())
        engine.update(delta)
    }
}
