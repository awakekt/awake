/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.engine.window

import com.awakekt.awake.core.host.clampFrameDelta
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.PrintLogSink
import com.awakekt.awake.engine.platform.WindowLifecycle
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent

/** Default mapping of DOM keyboard keys to engine [Key] values. */
val DefaultDomGameplayKeys: Map<String, Key> = linkedMapOf(
    "w" to Key.W,
    "a" to Key.A,
    "s" to Key.S,
    "d" to Key.D,
    "arrowup" to Key.ArrowUp,
    "arrowdown" to Key.ArrowDown,
    "arrowleft" to Key.ArrowLeft,
    "arrowright" to Key.ArrowRight,
    " " to Key.Space,
    "spacebar" to Key.Space,
    "escape" to Key.Escape,
    "f1" to Key.F1,
    "f2" to Key.F2,
    "f3" to Key.F3,
    "f4" to Key.F4,
    "f5" to Key.F5,
    "f6" to Key.F6,
    "f7" to Key.F7,
    "f8" to Key.F8,
    "f9" to Key.F9,
    "f10" to Key.F10,
    "f11" to Key.F11,
    "f12" to Key.F12,
)

/** `MouseEvent.button` value for the right mouse button. */
private const val DOM_MOUSE_BUTTON_SECONDARY = 2

/**
 * DOM `MouseEvent.button` codes as [PointerButton]s.
 *
 * The codes are not in the obvious order -- 1 is the middle button and 2 is the right -- and an
 * unknown code maps to nothing rather than defaulting to primary. Defaulting is what this bridge
 * used to do implicitly: every button that was not secondary took the primary branch, so a
 * middle-click actuated whatever was under the cursor.
 */
private val DOM_MOUSE_BUTTONS = mapOf(
    0 to PointerButton.Primary,
    1 to PointerButton.Middle,
    DOM_MOUSE_BUTTON_SECONDARY to PointerButton.Secondary,
    3 to PointerButton.Back,
    4 to PointerButton.Forward,
)

/**
 * Binds browser window mouse, touch, and wheel events to engine [input], in [canvas]'s buffer
 * pixels: each point is measured from the canvas's box, whichever element the event reached.
 */
fun bindWindowPointerInput(input: Input, canvas: HTMLCanvasElement = findCanvas()) {
    fun scaledPointer(event: MouseEvent): Pair<Float, Float> =
        canvas.box().bufferPoint(event.clientX.toDouble(), event.clientY.toDouble(), canvas.width, canvas.height)

    window.addEventListener("mousemove") { event ->
        val (x, y) = scaledPointer(event as MouseEvent)
        input.setPointer(input.pointerDown, x, y)
    }
    window.addEventListener("mousedown") { event ->
        val mouse = event as MouseEvent
        val (x, y) = scaledPointer(mouse)
        when (DOM_MOUSE_BUTTONS[mouse.button.toInt()]) {
            PointerButton.Primary -> input.setPointer(true, x, y)
            PointerButton.Secondary -> {
                input.setSecondaryPointer(true)
                // Still publish the position: shadcnContextMenu opens at the cursor.
                input.setPointer(input.pointerDown, x, y)
            }
            null -> input.setPointer(input.pointerDown, x, y)
            else -> {
                input.setButton(DOM_MOUSE_BUTTONS.getValue(mouse.button.toInt()), true)
                input.setPointer(input.pointerDown, x, y)
            }
        }
    }
    window.addEventListener("mouseup") { event ->
        val mouse = event as MouseEvent
        val (x, y) = scaledPointer(mouse)
        when (DOM_MOUSE_BUTTONS[mouse.button.toInt()]) {
            PointerButton.Primary -> input.setPointer(false, x, y)
            PointerButton.Secondary -> {
                input.setSecondaryPointer(false)
                input.setPointer(input.pointerDown, x, y)
            }
            null -> input.setPointer(input.pointerDown, x, y)
            else -> {
                input.setButton(DOM_MOUSE_BUTTONS.getValue(mouse.button.toInt()), false)
                input.setPointer(input.pointerDown, x, y)
            }
        }
    }
    // Without this the browser's own context menu covers the app's.
    window.addEventListener("contextmenu") { event -> event.preventDefault() }
    bindWindowTouchInput(input, canvas)
    val wheelModifiers = WheelModifiers(input) { release -> window.requestAnimationFrame { release() } }
    // Non-passive: a browser treats a wheel listener on the window as passive unless told otherwise,
    // and ignores preventDefault in a passive one.
    addNonPassiveListener("wheel") { event ->
        val wheel = event as WheelEvent
        // A trackpad pinch is a wheel with ctrlKey set, as Ctrl+wheel is on desktop, where Ctrl is a
        // held key beside the scroll. Forward the modifiers the same way.
        wheelModifiers.hold(wheelModifierKeys(wheel.ctrlKey, wheel.shiftKey, wheel.altKey, wheel.metaKey))
        // Accumulate the hardware delta until the runtime snapshots it, mirroring
        // GlfwInputBridge's `input.scrollDeltaY += reader.consumeScrollDeltaY()`, in its units and sign.
        input.scrollDeltaX += domWheelToScrollDelta(wheel.deltaX, wheel.deltaMode)
        input.scrollDeltaY += domWheelToScrollDelta(wheel.deltaY, wheel.deltaMode)
        // The canvas takes every wheel over it, or the page would scroll beneath the app and a pinch
        // (Ctrl+wheel) would zoom the whole page.
        if (canvas.box().contains(wheel.clientX.toDouble(), wheel.clientY.toDouble())) wheel.preventDefault()
    }
    // The same pinch again, typed: InputSnapshot.pinch, and Safari's gesture events.
    bindWindowPinchInput(input, canvas)
}

/** Binds browser window keyboard keydown, keyup, and blur events to engine [input]. */
fun bindWindowKeyboardInput(
    input: Input,
    keys: Map<String, Key> = DefaultDomGameplayKeys,
) {
    fun resolveKey(event: KeyboardEvent): Key? =
        keys[event.key.lowercase()]

    window.addEventListener("keydown") { event ->
        val key = resolveKey(event as KeyboardEvent) ?: return@addEventListener
        input.setKeyDown(key, true)
    }
    window.addEventListener("keyup") { event ->
        val key = resolveKey(event as KeyboardEvent) ?: return@addEventListener
        input.setKeyDown(key, false)
    }
    window.addEventListener("blur") {
        input.clearKeys()
    }
}

/** Feeds Awake's shared text-input API ([Input.pushTypedText]/[Input.pushEditAction]) from DOM
 * `keydown` for a host that wants keydown-only text. */
fun bindWindowTextInput(input: Input) {
    window.addEventListener("keydown") { event ->
        val keyboardEvent = event as KeyboardEvent
        val key = keyboardEvent.key
        DomEditKeys[key]?.let { action ->
            input.pushEditAction(action)
            return@addEventListener
        }
        val isPrintable =
            key.length == 1 && !keyboardEvent.ctrlKey && !keyboardEvent.metaKey && !keyboardEvent.altKey
        if (isPrintable) {
            input.pushTypedText(key)
        }
    }
}

/**
 * The pixel width and height [canvas]'s buffer needs: its box on the page at the device pixel ratio.
 * The box, not the window, since page CSS can make the two differ.
 */
fun currentCanvasSize(canvas: HTMLCanvasElement = findCanvas()): Pair<Int, Int> =
    currentCanvasSize(canvas, maxPixelRatio = null)

/**
 * The pixel width and height [canvas]'s buffer needs at [currentCanvasPixelRatio]: the device pixel
 * ratio, capped at [maxPixelRatio] (`null` for no cap).
 */
fun currentCanvasSize(canvas: HTMLCanvasElement, maxPixelRatio: Float?): Pair<Int, Int> =
    canvas.box().bufferSize(currentCanvasPixelRatio(maxPixelRatio))

/**
 * The pixel ratio a canvas buffer is drawn at, and the UI's density with it: the device pixel ratio,
 * capped at [maxPixelRatio] (`null` for no cap).
 */
fun currentCanvasPixelRatio(maxPixelRatio: Float?): Double =
    canvasPixelRatio(currentWindowDensity(), maxPixelRatio)

/** Returns the current browser window device pixel ratio. */
fun currentWindowDensity(): Double {
    val scale = window.devicePixelRatio
    return if (scale.isFinite() && scale > 0.0) scale else 1.0
}

/** Sets the internal pixel buffer dimensions of [canvas]. */
fun syncCanvasSize(canvas: HTMLCanvasElement, width: Int, height: Int) {
    canvas.width = width
    canvas.height = height
}

/**
 * Finds an HTMLCanvasElement by [canvasId], or throws if not found.
 */
fun findCanvas(canvasId: String = "awake-canvas"): HTMLCanvasElement =
    document.getElementById(canvasId) as? HTMLCanvasElement
        ?: error("No canvas found with id '$canvasId'.")

/**
 * Runs a browser canvas window host for the provided [lifecycle].
 *
 * Automatically sizes the canvas, binds pointer, keyboard, and [DomTextInputBridge] input,
 * listens to window resize, executes [initBackend] asynchronously, and runs the animation frame loop.
 *
 * [lifecycle] is resized to the canvas before [initBackend] runs, so an app that is created there
 * already knows its viewport, as on the other platforms.
 *
 * @param canvas The target [HTMLCanvasElement] to render into.
 * @param lifecycle The [WindowLifecycle] driving this game/app session.
 * @param onResize Hook invoked when window resize occurs, before [lifecycle.resize].
 * @param isStopped Checked before every frame; once it answers true the loop stops scheduling
 *                  frames, as a backend whose device failed asynchronously needs.
 * @param cursor The pointer shape the UI asks for this frame, or `null` to leave the canvas's cursor
 *               alone. It is read after every frame and set as the canvas's CSS `cursor` when it
 *               changes, as the desktop window does with GLFW.
 * @param initBackend Hook invoked asynchronously on [MainScope] after input binding and initial canvas sync,
 *                    allowing the rendering backend (e.g. WebGPU) to initialize its device, context, or surface.
 */
fun runBrowserCanvas(
    canvas: HTMLCanvasElement,
    lifecycle: WindowLifecycle,
    onResize: (width: Int, height: Int) -> Unit = { _, _ -> },
    isStopped: () -> Boolean = { false },
    cursor: (() -> PointerCursor)? = null,
    initBackend: suspend (canvas: HTMLCanvasElement, width: Int, height: Int) -> Unit = { _, _, _ -> },
) {
    runBrowserCanvas(canvas, lifecycle, onResize, isStopped, cursor, maxPixelRatio = { null }, initBackend)
}

/**
 * Runs a browser canvas window host for [lifecycle], as the overload without [maxPixelRatio] does,
 * with the canvas's buffer drawn at no more than [maxPixelRatio] buffer pixels per CSS pixel.
 *
 * [maxPixelRatio] is read on every resize and before every frame, so a game can expose it as a
 * graphics setting: a new value resizes the buffer on the next frame. `null` is no cap, the device
 * pixel ratio. The cap scales the whole buffer, UI included; the UI's density is the capped ratio, so
 * layout keeps its size on the page and pointers land where it was drawn.
 */
@Suppress("TooGenericExceptionCaught", "LongParameterList")
fun runBrowserCanvas(
    canvas: HTMLCanvasElement,
    lifecycle: WindowLifecycle,
    onResize: (width: Int, height: Int) -> Unit = { _, _ -> },
    isStopped: () -> Boolean = { false },
    cursor: (() -> PointerCursor)? = null,
    maxPixelRatio: () -> Float?,
    initBackend: suspend (canvas: HTMLCanvasElement, width: Int, height: Int) -> Unit = { _, _, _ -> },
) {
    if (!Log.hasSinks) Log.install(PrintLogSink(minimumLevel = LogLevel.Warn))
    val input = lifecycle.input

    bindWindowPointerInput(input, canvas)
    bindWindowKeyboardInput(input)
    val textInput = DomTextInputBridge(input)
    val canvasCursor = CanvasCursor { css -> canvas.style.cursor = css }

    var pixelRatio = currentCanvasPixelRatio(maxPixelRatio())
    fun syncCanvas(): Pair<Int, Int> {
        pixelRatio = currentCanvasPixelRatio(maxPixelRatio())
        val size = canvas.box().bufferSize(pixelRatio)
        syncCanvasSize(canvas, size.first, size.second)
        lifecycle.setDensity(pixelRatio.toFloat())
        lifecycle.setSafeArea(currentSafeArea(canvas))
        return size
    }
    fun resizeCanvas() {
        val (width, height) = syncCanvas()
        onResize(width, height)
        lifecycle.resize(x = 0, y = 0, width = width, height = height)
    }

    val initialSize = syncCanvas()
    window.addEventListener("resize") { resizeCanvas() }

    MainScope().launch {
        try {
            lifecycle.resize(
                x = 0,
                y = 0,
                width = initialSize.first,
                height = initialSize.second,
            )
            initBackend(canvas, initialSize.first, initialSize.second)

            var lastFrameTime = window.performance.now()
            var frameLoopStopped = false
            fun frame(time: Double) {
                if (frameLoopStopped || isStopped()) {
                    frameLoopStopped = true
                    return
                }
                // The first frame's timestamp can come before lastFrameTime's performance.now().
                val deltaSeconds = clampFrameDelta((time - lastFrameTime) / 1000.0).toFloat()
                lastFrameTime = time
                try {
                    // A changed cap, or a device ratio that moved without a resize event.
                    if (currentCanvasPixelRatio(maxPixelRatio()) != pixelRatio) resizeCanvas()
                    pollBrowserGamepad(input)
                    lifecycle.update(deltaSeconds)
                    textInput.sync()
                    cursor?.let { canvasCursor.show(it()) }
                } catch (error: Throwable) {
                    frameLoopStopped = true
                    reportBrowserFrameFailure(
                        "Browser frame failed: ${error.message ?: error::class.simpleName}",
                    )
                    return
                }
                window.requestAnimationFrame(::frame)
            }
            window.requestAnimationFrame(::frame)
        } catch (error: Throwable) {
            reportBrowserStartupFailure(
                "Browser window startup failed before the first frame: ${error.message ?: error::class.simpleName}",
            )
        }
    }
}

/** Keeps startup failures visible in browser console instead of leaving a black canvas. */
@JsFun("(message) => console.error(message)")
private external fun reportBrowserStartupFailure(message: String)

/** Keeps frame failures visible in browser console instead of leaving a black canvas. */
@JsFun("(message) => console.error(message)")
private external fun reportBrowserFrameFailure(message: String)
