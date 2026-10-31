/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.application

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.engine.window.findCanvas
import web.html.HTMLCanvasElement

/*
 * The launchWebGpuGame overloads that take a maxPixelRatio provider. They are overloads, not new
 * defaulted parameters on the existing functions, so the published signatures stay as they were.
 * A game exposes the cap as a graphics setting, for example
 * `launchWebGpuGame(game, plan, maxPixelRatio = { settings.maxPixelRatio })`.
 */

/**
 * Starts [game] on the `<canvas>` with [canvasId], drawn by a [WebGpuEngine] built from [plan], at
 * no more than [maxPixelRatio] buffer pixels per CSS pixel. See the canvas overload that takes an
 * application factory for what the cap scales.
 */
fun launchWebGpuGame(
    game: AwakeAppLifecycle,
    plan: RenderPlan,
    canvasId: String = "awake-canvas",
    cursor: (() -> PointerCursor)? = null,
    maxPixelRatio: () -> Float?,
) {
    launchWebGpuGame(canvasId = canvasId, cursor = cursor, maxPixelRatio = maxPixelRatio) {
        WebGpuEngine(appLifecycle = game, requestedPlan = plan)
    }
}

/**
 * Starts [game] on [canvas], drawn by a [WebGpuEngine] built from [plan], at no more than
 * [maxPixelRatio] buffer pixels per CSS pixel.
 */
fun launchWebGpuGame(
    game: AwakeAppLifecycle,
    plan: RenderPlan,
    canvas: HTMLCanvasElement,
    cursor: (() -> PointerCursor)? = null,
    maxPixelRatio: () -> Float?,
) {
    launchWebGpuGame(canvas = canvas, cursor = cursor, maxPixelRatio = maxPixelRatio) {
        WebGpuEngine(appLifecycle = game, requestedPlan = plan)
    }
}

/**
 * Starts the engine [applicationFactory] builds on the `<canvas>` with [canvasId], at no more than
 * [maxPixelRatio] buffer pixels per CSS pixel.
 */
fun launchWebGpuGame(
    canvasId: String = "awake-canvas",
    cursor: (() -> PointerCursor)? = null,
    maxPixelRatio: () -> Float?,
    applicationFactory: () -> WebGpuEngine,
) {
    launchWebGpuGame(findCanvas(canvasId).unsafeCast<HTMLCanvasElement>(), cursor, maxPixelRatio, applicationFactory)
}
