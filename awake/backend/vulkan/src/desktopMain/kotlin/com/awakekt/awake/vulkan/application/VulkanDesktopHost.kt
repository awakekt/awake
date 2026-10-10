/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.application

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.engine.window.DesktopRunLimits
import com.awakekt.awake.engine.window.pollGlfwInput
import com.awakekt.awake.engine.window.runDesktopWindow
import java.io.File

/**
 * Runs a Vulkan desktop app using Awake's standard engine bootstrap.
 *
 * This is the dependency-only entry point: consumers provide the app lifecycle and the render
 * plan, while Awake owns construction of [VulkanEngine] and the GLFW frame loop. The factory
 * overload below remains available when an app needs a custom [VulkanEngine] subclass.
 */
fun runVulkanDesktopGame(
    game: AwakeAppLifecycle,
    plan: RenderPlan,
    pollInput: (window: Long, input: Input) -> Unit = ::pollGlfwInput,
    beforeFrame: () -> Unit = {},
    afterLoop: () -> Unit = {},
    cursor: (() -> PointerCursor)? = null,
) {
    runVulkanDesktopGame(
        game = game,
        applicationFactory = { lifecycle ->
            VulkanEngine(
                appLifecycle = lifecycle,
                plan = plan,
            )
        },
        pollInput = pollInput,
        beforeFrame = beforeFrame,
        afterLoop = afterLoop,
        cursor = cursor,
    )
}

/**
 * Runs [AwakeAppLifecycle] in a desktop window from `awake:engine:window`, drawn by the given
 * [VulkanEngine].
 *
 * Consumers still own input polling, debug channels, and which [VulkanEngine] instance to run.
 */
fun runVulkanDesktopGame(
    game: AwakeAppLifecycle,
    applicationFactory: (AwakeAppLifecycle) -> VulkanEngine,
    pollInput: (window: Long, input: Input) -> Unit = ::pollGlfwInput,
    beforeFrame: () -> Unit = {},
    afterLoop: () -> Unit = {},
    cursor: (() -> PointerCursor)? = null,
) {
    runVulkanDesktopGame(
        game = game,
        application = applicationFactory(game),
        pollInput = pollInput,
        beforeFrame = beforeFrame,
        afterLoop = afterLoop,
        cursor = cursor,
    )
}

/**
 * Runs [game] in a desktop window drawn by an existing [application], and returns once the window
 * has closed.
 *
 * Consumers still own input polling, debug channels and the engine instance. The window, and GLFW,
 * are torn down by this call.
 *
 * [limits] end the run early, for a CI job or a smoke check, and are read from `-Dawake.frames` and
 * `-Dawake.capture` by default. With a capture, [game] plays with no window, which skips
 * [pollInput], [cursor] and [afterLoop]; its last frame is written as a PNG.
 *
 * @param game The app whose window configuration, input and frame loop this serves.
 * @param application The engine that draws [game]'s frames.
 * @param pollInput Reads the window's keys and pointer into the game's input each frame.
 * @param beforeFrame Runs after input is read and before the frame is updated.
 * @param afterLoop Runs once the loop ends, before the engine is disposed.
 * @param cursor The pointer shape the UI asks for this frame, or `null` to leave the cursor alone.
 * @param limits How many frames to play, and whether to capture the last; see [DesktopRunLimits].
 * @throws IllegalStateException If [game] asks for a window backend other than Vulkan or the
 * default, or a limited run's engine never starts.
 */
@Suppress("LongParameterList")
fun runVulkanDesktopGame(
    game: AwakeAppLifecycle,
    application: VulkanEngine,
    pollInput: (window: Long, input: Input) -> Unit = ::pollGlfwInput,
    beforeFrame: () -> Unit = {},
    afterLoop: () -> Unit = {},
    // The desktop cursor application point: the UI owns the request (see PointerCursor's doc
    // comment), this loop owns the platform call. `null` (default) skips it entirely -- every
    // existing caller keeps its current zero-cursor-management behavior; a caller opts in by
    // returning its own UiContext's `finishFrame().effects.cursor` each frame.
    cursor: (() -> PointerCursor)? = null,
    limits: DesktopRunLimits = DesktopRunLimits.fromSystemProperties(),
) {
    check(
        game.windowConfig.backend == AppWindowBackend.VULKAN ||
            game.windowConfig.backend == AppWindowBackend.DEFAULT,
    ) {
        "Desktop Vulkan host requires a Vulkan or DEFAULT backend, found ${game.windowConfig.backend}."
    }
    limits.capture?.let { file ->
        val frames = limits.frames ?: DesktopRunLimits.DEFAULT_CAPTURE_FRAMES
        application.playHeadless(game.windowConfig.width, game.windowConfig.height, frames, beforeFrame = beforeFrame)
            .writePng(File(file))
        return
    }
    var started = false
    runDesktopWindow(
        game = game,
        onCreate = application::create,
        onFrame = application::update,
        onDispose = application::dispose,
        pollInput = pollInput,
        beforeFrame = beforeFrame,
        afterLoop = {
            started = application.isReady
            afterLoop()
        },
        cursor = cursor,
        frames = limits.frames,
    )
    // A limited run is a check: frames played by an engine that never started prove nothing.
    if (limits.frames != null) check(started) { notStarted(application) }
}
