/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.application

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.core.input.CursorImage
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.engine.window.findCanvas
import web.html.HTMLCanvasElement

/*
 * The launchWebGpuGame overloads that take a cursorImage provider: a custom cursor image, shown on
 * the canvas as a CSS `url(data:image/png;base64,...) x y, auto` cursor with its hotspot, in place
 * of the system shape [cursor] asks for. Overloads, not new defaulted parameters on the existing
 * functions, so the published signatures stay as they were. A game passes the same providers the
 * desktop host takes, for example
 * `launchWebGpuGame(game, plan, cursor = pointerCursorOf(game), cursorImage = pointerCursorImageOf(game))`.
 *
 * Each takes the optional maxPixelRatio and renderScale providers the other overloads split among
 * themselves; omit them for the defaults (no cap, full scale).
 */

/**
 * Starts [game] on the `<canvas>` with [canvasId], drawn by a [WebGpuEngine] built from [plan], with
 * [cursorImage] shown as the canvas's cursor while it returns a non-null image.
 *
 * @param game The application lifecycle to run.
 * @param plan The shared render plan to narrow for WebGPU and build pipelines from.
 * @param canvasId The id of the canvas element to render into.
 * @param cursor The pointer shape the UI asks for this frame, or `null` to leave the canvas's cursor
 * alone. It shows while [cursorImage] returns `null`.
 * @param cursorImage The custom cursor image to show this frame, or `null` for [cursor]'s shape.
 * Pass `pointerCursorImageOf(game)`. Return one held instance, not a new image per frame: the
 * browser string is built once per instance.
 * @param maxPixelRatio The most buffer pixels per CSS pixel, or `null` for the device pixel ratio.
 * @param renderScale The fraction of the buffer's size the 3D scene renders at, or `null` for full.
 */
@Suppress("LongParameterList")
fun launchWebGpuGame(
    game: AwakeAppLifecycle,
    plan: RenderPlan,
    canvasId: String = "awake-canvas",
    cursor: (() -> PointerCursor)? = null,
    cursorImage: () -> CursorImage?,
    maxPixelRatio: () -> Float? = { null },
    renderScale: (() -> Float)? = null,
) {
    launchWebGpuGame(game, plan, findCanvas(canvasId).unsafeCast<HTMLCanvasElement>(), cursor, cursorImage, maxPixelRatio, renderScale)
}

/**
 * Starts [game] on [canvas], drawn by a [WebGpuEngine] built from [plan], with [cursorImage] shown
 * as the canvas's cursor; see the canvas-id overload that takes a cursor image.
 */
@Suppress("LongParameterList")
fun launchWebGpuGame(
    game: AwakeAppLifecycle,
    plan: RenderPlan,
    canvas: HTMLCanvasElement,
    cursor: (() -> PointerCursor)? = null,
    cursorImage: () -> CursorImage?,
    maxPixelRatio: () -> Float? = { null },
    renderScale: (() -> Float)? = null,
) {
    startWebGpuGame(canvas, cursor, maxPixelRatio, renderScale, cursorImage) {
        WebGpuEngine(appLifecycle = game, requestedPlan = plan)
    }
}

/**
 * Starts the engine [applicationFactory] builds on the `<canvas>` with [canvasId], with [cursorImage]
 * shown as the canvas's cursor; see the overload that takes a game and a plan.
 */
@Suppress("LongParameterList")
fun launchWebGpuGame(
    canvasId: String = "awake-canvas",
    cursor: (() -> PointerCursor)? = null,
    cursorImage: () -> CursorImage?,
    maxPixelRatio: () -> Float? = { null },
    renderScale: (() -> Float)? = null,
    applicationFactory: () -> WebGpuEngine,
) {
    launchWebGpuGame(findCanvas(canvasId).unsafeCast<HTMLCanvasElement>(), cursor, cursorImage, maxPixelRatio, renderScale, applicationFactory)
}

/**
 * Starts the engine [applicationFactory] builds on [canvas], with [cursorImage] shown as the
 * canvas's cursor; see the overload that takes a game and a plan.
 *
 * @throws IllegalStateException If the browser or device does not expose WebGPU.
 */
@Suppress("LongParameterList")
fun launchWebGpuGame(
    canvas: HTMLCanvasElement,
    cursor: (() -> PointerCursor)? = null,
    cursorImage: () -> CursorImage?,
    maxPixelRatio: () -> Float? = { null },
    renderScale: (() -> Float)? = null,
    applicationFactory: () -> WebGpuEngine,
) {
    startWebGpuGame(canvas, cursor, maxPixelRatio, renderScale, cursorImage, applicationFactory)
}
