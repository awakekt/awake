/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.application

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.core.host.DesktopFrameLoop
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.vulkan.gen.VulkanWindow

private const val GLFW_CLIENT_API = 0x00022001
private const val GLFW_NO_API = 0

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
 * Reusable desktop GLFW host for a Vulkan-backed [AwakeAppLifecycle].
 *
 * Consumers still own authored concerns such as input polling, debug channels, and which
 * [VulkanEngine] instance to run. This helper only centralizes the window +
 * frame-loop boilerplate every Vulkan desktop sample would otherwise copy.
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
) {
    check(game.windowConfig.backend == AppWindowBackend.VULKAN) {
        "Desktop Vulkan host requires a Vulkan backend, found ${game.windowConfig.backend}."
    }
    check(VulkanWindow.glfwInit()) { "glfwInit failed" }
    VulkanWindow.glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
    val window = VulkanWindow.glfwCreateWindow(
        game.windowConfig.width,
        game.windowConfig.height,
        game.windowConfig.title,
    )
    check(window != 0L) { "glfwCreateWindow returned null" }
    VulkanWindow.glfwFocusWindow(window)
    VulkanWindow.glfwSetScrollCallback(window)

    try {
        application.create(window)
        while (!VulkanWindow.glfwWindowShouldClose(window)) {
            VulkanWindow.glfwPollEvents()
            pollInput(window, game.input)
            pollGlfwTextInput(window, game.input)
            beforeFrame()
            DesktopFrameLoop.tick { deltaTime ->
                application.update(deltaTime.toFloat())
            }
            cursor?.let { applyUiCursor(window, it()) }
        }
    } finally {
        afterLoop()
        application.dispose()
    }
}
