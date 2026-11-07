/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.host.DesktopFrameLoop
import com.awakekt.awake.core.input.CursorImage
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle

private const val GLFW_CLIENT_API = 0x00022001
private const val GLFW_NO_API = 0
private const val GLFW_TRUE = 1
private const val GLFW_FALSE = 0

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
 * @param cursorImage A custom cursor image to show this frame, overriding [cursor] while non-null;
 * `null` (or a provider returning `null`) falls back to [cursor]. See [applyCustomCursor].
 * @param frames How many frames to run before ending the loop as if the window had closed, such as
 * a smoke check's [DesktopRunLimits.frames]; null runs until the window closes.
 * @param onDpi Hears the real dots per inch of the monitor the window is on, after [onCreate] and
 * whenever the window moves to a monitor that differs; 0 when the monitor reports no size.
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
    cursorImage: (() -> CursorImage?)? = null,
    frames: Int? = null,
    onDpi: (dpi: Float) -> Unit = {},
) = runDesktopWindow(
    game, DesktopWindowOptions(), onCreate, onFrame, onDispose, pollInput, beforeFrame, afterLoop, cursor,
    cursorImage, frames, onDpi,
)

/**
 * [runDesktopWindow] opened with [options], such as frameless with
 * `desktopWindowOptions { decoration = WindowDecoration.None }`. Every other parameter is the same.
 *
 * [onDensity] hears the UI density, framebuffer pixels per dp, that [options]' `displayScale` asks
 * for: after [onCreate], and again whenever the OS display scale changes, as when the window moves to
 * a monitor with another scale. Pass it to the engine's `setDensity`.
 */
@Suppress("LongParameterList")
fun runDesktopWindow(
    game: AwakeAppLifecycle,
    options: DesktopWindowOptions,
    onCreate: (window: Long) -> Unit,
    onFrame: (deltaSeconds: Float) -> Unit,
    onDispose: () -> Unit,
    pollInput: (window: Long, input: Input) -> Unit = ::pollGlfwInput,
    beforeFrame: () -> Unit = {},
    afterLoop: () -> Unit = {},
    cursor: (() -> PointerCursor)? = null,
    cursorImage: (() -> CursorImage?)? = null,
    frames: Int? = null,
    onDpi: (dpi: Float) -> Unit = {},
    onDensity: (density: Float) -> Unit = {},
) {
    require(frames == null || frames > 0) { "A run lasts at least one frame; was $frames." }
    check(GlfwWindow.glfwInit()) { "glfwInit failed" }
    GlfwWindow.glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
    val decorated = if (options.decoration == WindowDecoration.System) GLFW_TRUE else GLFW_FALSE
    GlfwWindow.glfwWindowHint(GLFW_DECORATED, decorated)
    val window = GlfwWindow.glfwCreateWindow(
        game.windowConfig.width,
        game.windowConfig.height,
        game.windowConfig.title,
    )
    check(window != 0L) { "glfwCreateWindow returned null" }
    GlfwWindow.glfwFocusWindow(window)
    GlfwWindow.glfwSetScrollCallback(window)
    GlfwWindow.glfwSetCharCallback(window)

    try {
        try {
            onCreate(window)
            val dpi = MonitorDpi(window, onDpi)
            val density = DisplayDensity(options.displayScale, glfwDisplayScaleReader(window), onDensity)
            val checkDisplay = { dpi.check(); density.check() }
            runFrames(window, game, onFrame, pollInput, beforeFrame = { checkDisplay(); beforeFrame() }, cursor, cursorImage, frames)
        } finally {
            afterLoop()
            onDispose()
        }
    } finally {
        GlfwWindow.glfwDestroyWindow(window)
        GlfwWindow.glfwTerminate()
    }
}

/** The options [runDesktopWindow] as it was before `onDensity`: hidden, and kept so a consumer compiled against it still links. */
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
@Suppress("LongParameterList")
fun runDesktopWindow(
    game: AwakeAppLifecycle,
    options: DesktopWindowOptions,
    onCreate: (window: Long) -> Unit,
    onFrame: (deltaSeconds: Float) -> Unit,
    onDispose: () -> Unit,
    pollInput: (window: Long, input: Input) -> Unit = ::pollGlfwInput,
    beforeFrame: () -> Unit = {},
    afterLoop: () -> Unit = {},
    cursor: (() -> PointerCursor)? = null,
    cursorImage: (() -> CursorImage?)? = null,
    frames: Int? = null,
    onDpi: (dpi: Float) -> Unit = {},
) = runDesktopWindow(
    game, options, onCreate, onFrame, onDispose, pollInput, beforeFrame, afterLoop, cursor, cursorImage, frames,
    onDpi, onDensity = {},
)

/** [runDesktopWindow] as it was before `cursorImage`: hidden, and kept so a consumer compiled against it still links. */
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
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
    onDpi: (dpi: Float) -> Unit = {},
) {
    runDesktopWindow(
        game = game,
        onCreate = onCreate,
        onFrame = onFrame,
        onDispose = onDispose,
        pollInput = pollInput,
        beforeFrame = beforeFrame,
        afterLoop = afterLoop,
        cursor = cursor,
        cursorImage = null,
        frames = frames,
        onDpi = onDpi,
    )
}

@Suppress("LongParameterList")
private fun runFrames(
    window: Long,
    game: AwakeAppLifecycle,
    onFrame: (deltaSeconds: Float) -> Unit,
    pollInput: (window: Long, input: Input) -> Unit,
    beforeFrame: () -> Unit,
    cursor: (() -> PointerCursor)?,
    cursorImage: (() -> CursorImage?)?,
    frames: Int?,
) {
    var played = 0
    while (!GlfwWindow.glfwWindowShouldClose(window) && (frames == null || played < frames)) {
        GlfwWindow.glfwPollEvents()
        val isFocused = GlfwWindow.glfwGetWindowAttrib(window, GLFW_FOCUSED) != 0
        DesktopFrameLoop.isWindowFocused = isFocused
        pollInput(window, game.input)
        pollGlfwTextInput(window, game.input)
        pollGlfwGamepad(game.input, focused = isFocused)
        beforeFrame()
        val effectiveMode = game.windowConfig.effectiveFrameRateMode(isFocused)
        DesktopFrameLoop.tick(effectiveMode) { deltaTime ->
            onFrame(deltaTime.toFloat())
            played++
        }
        // A custom image, when the app asks for one this frame, wins over the system shape.
        val image = cursorImage?.invoke()
        if (image != null) applyCustomCursor(window, image) else cursor?.let { applyUiCursor(window, it()) }
    }
}
