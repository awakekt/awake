/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.host.DesktopFrameLoop
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle

private const val GLFW_CLIENT_API = 0x00022001
private const val GLFW_NO_API = 0

/**
 * Opens a desktop window for [game] and runs its frame loop until the window is closed, or for
 * [frames] frames.
 *
 * The window has no graphics context of its own; the backend draws into it. This function owns the
 * window's lifetime: it creates it, destroys it after [onDispose], and terminates GLFW.
 *
 * @param game The app whose window size, title, frame-rate mode and input this window serves.
 * @param onCreate Receives the native window handle once, to create the backend's surface.
 * @param onFrame Runs once per frame with the frame's delta in seconds.
 * @param onDispose Releases the backend before the window is destroyed. Runs even if [onCreate] threw.
 * @param pollInput Reads the window's keys and pointer into [game]'s input each frame.
 * @param beforeFrame Runs after input is read and before [onFrame].
 * @param afterLoop Runs once the loop ends, before [onDispose].
 * @param cursor The pointer shape the UI asks for this frame, or `null` to leave it alone.
 * @param frames How many frames to run before ending the loop as if the window had closed, such as
 * a smoke check's [DesktopRunLimits.frames]; null runs until the window closes.
 */
@Suppress("LongParameterList")
fun runDesktopWindow(
    game: AwakeAppLifecycle,
    onCreate: (window: Long) -> Unit,
    onFrame: (deltaSeconds: Float) -> Unit,
    onDispose: () -> Unit,
    pollInput: (window: Long, input: Input) -> Unit = ::pollGlfwInput,
    beforeFrame: () -> Unit = {},
    afterLoop: () -> Unit = {},
    cursor: (() -> PointerCursor)? = null,
    frames: Int? = null,
) {
    require(frames == null || frames > 0) { "A run lasts at least one frame; was $frames." }
    check(GlfwWindow.glfwInit()) { "glfwInit failed" }
    GlfwWindow.glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
    val window = GlfwWindow.glfwCreateWindow(
        game.windowConfig.width,
        game.windowConfig.height,
        game.windowConfig.title,
    )
    check(window != 0L) { "glfwCreateWindow returned null" }
    GlfwWindow.glfwFocusWindow(window)
    GlfwWindow.glfwSetScrollCallback(window)

    try {
        try {
            onCreate(window)
            runFrames(window, game, onFrame, pollInput, beforeFrame, cursor, frames)
        } finally {
            afterLoop()
            onDispose()
        }
    } finally {
        GlfwWindow.glfwDestroyWindow(window)
        GlfwWindow.glfwTerminate()
    }
}

@Suppress("LongParameterList")
private fun runFrames(
    window: Long,
    game: AwakeAppLifecycle,
    onFrame: (deltaSeconds: Float) -> Unit,
    pollInput: (window: Long, input: Input) -> Unit,
    beforeFrame: () -> Unit,
    cursor: (() -> PointerCursor)?,
    frames: Int?,
) {
    var played = 0
    while (!GlfwWindow.glfwWindowShouldClose(window) && (frames == null || played < frames)) {
        GlfwWindow.glfwPollEvents()
        val isFocused = GlfwWindow.glfwGetWindowAttrib(window, GLFW_FOCUSED) != 0
        DesktopFrameLoop.isWindowFocused = isFocused
        pollInput(window, game.input)
        pollGlfwTextInput(window, game.input)
        beforeFrame()
        val effectiveMode = game.windowConfig.effectiveFrameRateMode(isFocused)
        DesktopFrameLoop.tick(effectiveMode) { deltaTime ->
            onFrame(deltaTime.toFloat())
            played++
        }
        cursor?.let { applyUiCursor(window, it()) }
    }
}
