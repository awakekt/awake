/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.application

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.PointerButton
import io.ygdrasil.webgpu.CompositeAlphaMode
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.SurfaceConfiguration
import io.ygdrasil.webgpu.WGPUContext
import io.ygdrasil.webgpu.canvasContextRenderer
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent
import web.dom.ElementId
import web.dom.document
import web.html.HTMLCanvasElement

/**
 * Reusable WebGPU canvas host for authored games.
 */
fun launchWebGpuGame(
    canvasId: String = "awake-canvas",
    applicationFactory: () -> WebGpuEngine,
) {
    val canvas = document.getElementById(ElementId(canvasId)) as? HTMLCanvasElement
        ?: error("No canvas found with id '$canvasId'.")
    launchWebGpuGame(canvas, applicationFactory)
}

fun launchWebGpuGame(
    canvas: HTMLCanvasElement,
    applicationFactory: () -> WebGpuEngine,
) {
    val resolvedApplication = applicationFactory()
    val input = resolvedApplication.input

    bindWindowPointerInput(input)
    bindWindowKeyboardInput(input)
    bindWindowTextInput(input)

    val initialSize = currentCanvasSize()
    syncCanvasSize(canvas, initialSize.first, initialSize.second)
    var wgpuContext: WGPUContext? = null
    var application: WebGpuEngine? = resolvedApplication

    window.addEventListener("resize") {
        val (width, height) = currentCanvasSize()
        syncCanvasSize(canvas, width, height)
        wgpuContext?.let(::configureSurface)
        application?.resize(x = 0, y = 0, width = width, height = height)
    }

    MainScope().launch {
        val canvasContext = canvasContextRenderer(
            htmlCanvas = canvas,
            width = initialSize.first,
            height = initialSize.second,
        )
        val resolvedContext = canvasContext.wgpuContext
        wgpuContext = resolvedContext
        configureSurface(resolvedContext)

        resolvedApplication.resize(
            x = 0,
            y = 0,
            width = initialSize.first,
            height = initialSize.second,
        )
        resolvedApplication.create(resolvedContext)

        var lastFrameTime = window.performance.now()
        fun frame(time: Double) {
            val deltaSeconds = ((time - lastFrameTime) / 1000.0).toFloat()
            lastFrameTime = time
            resolvedApplication.update(deltaSeconds)
            window.requestAnimationFrame(::frame)
        }
        window.requestAnimationFrame(::frame)
    }
}

val DefaultDomGameplayKeys: Map<String, com.awakekt.awake.core.input.Key> = linkedMapOf(
    "w" to com.awakekt.awake.core.input.Key.W,
    "a" to com.awakekt.awake.core.input.Key.A,
    "s" to com.awakekt.awake.core.input.Key.S,
    "d" to com.awakekt.awake.core.input.Key.D,
    "arrowup" to com.awakekt.awake.core.input.Key.ArrowUp,
    "arrowdown" to com.awakekt.awake.core.input.Key.ArrowDown,
    "arrowleft" to com.awakekt.awake.core.input.Key.ArrowLeft,
    "arrowright" to com.awakekt.awake.core.input.Key.ArrowRight,
    " " to com.awakekt.awake.core.input.Key.Space,
    "spacebar" to com.awakekt.awake.core.input.Key.Space,
    "escape" to com.awakekt.awake.core.input.Key.Escape,
    "f1" to com.awakekt.awake.core.input.Key.F1,
    "f2" to com.awakekt.awake.core.input.Key.F2,
    "f3" to com.awakekt.awake.core.input.Key.F3,
    "f4" to com.awakekt.awake.core.input.Key.F4,
    "f5" to com.awakekt.awake.core.input.Key.F5,
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
 *
 * A map rather than a function because this file is at detekt's per-file function ceiling, and a
 * lookup table is what this is anyway.
 */
private val DOM_MOUSE_BUTTONS = mapOf(
    0 to PointerButton.Primary,
    1 to PointerButton.Middle,
    DOM_MOUSE_BUTTON_SECONDARY to PointerButton.Secondary,
    3 to PointerButton.Back,
    4 to PointerButton.Forward,
)

fun bindWindowPointerInput(input: Input) {
    fun scaledPointer(event: MouseEvent): Pair<Float, Float> {
        val density = currentWindowDensity()
        return Pair(
            (event.offsetX * density).toFloat(),
            (event.offsetY * density).toFloat(),
        )
    }

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
            // Held for anyone reading `buttonsDown`, but not a primary press: this branch used to
            // fall through to `setPointer(true)` and made a middle-click actuate the UI.
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
    window.addEventListener("wheel") { event ->
        val wheel = event as WheelEvent
        // Accumulate the hardware delta until the runtime snapshots it, mirroring
        // GlfwInputBridge's `input.scrollDeltaY += reader.consumeScrollDeltaY()`.
        input.scrollDeltaX += normalizeWheelDelta(wheel.deltaX, wheel.deltaMode)
        input.scrollDeltaY += normalizeWheelDelta(wheel.deltaY, wheel.deltaMode)
    }
}

/**
 * Browsers report wheel deltas in different units depending on `deltaMode`: raw pixels
 * (Chrome's default for most mice/trackpads), "lines" (Firefox with some mice), or "pages".
 * Desktop's GLFW scroll callback reports ~1.0 per notch (line-like), so normalize pixel/page
 * deltas down to that same rough scale rather than feeding [Input.scrollDeltaX]/[scrollDeltaY]
 * wildly different magnitudes per platform -- both get multiplied by the same
 * `UiScrollConfig.scrollSpeed` downstream in ScrollContainers.kt.
 */
private fun normalizeWheelDelta(delta: Double, deltaMode: Int): Float = when (deltaMode) {
    WheelEvent.DOM_DELTA_LINE -> delta.toFloat()
    WheelEvent.DOM_DELTA_PAGE -> (delta * 32.0).toFloat()
    else -> (delta / 100.0).toFloat() // DOM_DELTA_PIXEL
}

fun bindWindowKeyboardInput(
    input: Input,
    keys: Map<String, com.awakekt.awake.core.input.Key> = DefaultDomGameplayKeys,
) {
    fun resolveKey(event: KeyboardEvent): com.awakekt.awake.core.input.Key? =
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

/** `KeyboardEvent.key` -> [TextEditAction] for the same discrete edit set
 * [GlfwTextInputBridge]/[AwakeUIKitTextInputBridge] push on desktop/iOS. */
private val DomEditKeys: Map<String, com.awakekt.awake.core.input.TextEditAction> =
    mapOf(
        "Backspace" to com.awakekt.awake.core.input.TextEditAction.Backspace,
        "Delete" to com.awakekt.awake.core.input.TextEditAction.Delete,
        "Enter" to com.awakekt.awake.core.input.TextEditAction.Enter,
        "ArrowLeft" to com.awakekt.awake.core.input.TextEditAction.ArrowLeft,
        "ArrowRight" to com.awakekt.awake.core.input.TextEditAction.ArrowRight,
        "ArrowUp" to com.awakekt.awake.core.input.TextEditAction.ArrowUp,
        "ArrowDown" to com.awakekt.awake.core.input.TextEditAction.ArrowDown,
        "Home" to com.awakekt.awake.core.input.TextEditAction.Home,
        "End" to com.awakekt.awake.core.input.TextEditAction.End,
    )

/** Feeds Awake's shared text-input API ([Input.pushTypedText]/[Input.pushEditAction]) from DOM
 * `keydown` -- the wasmJs sibling of [GlfwTextInputBridge]'s polling loop, but far simpler:
 * `KeyboardEvent.key` already resolves the printable character (Unicode/shift/layout-aware,
 * unlike GLFW's raw scancodes) and the OS/browser already auto-repeats `keydown` while a key
 * is held, so there's no character map or hold-to-repeat state machine to hand-roll here --
 * only which events count as "typed text" vs. a discrete [TextEditAction] to forward.
 *
 * Runs unconditionally on every `keydown`, same as the desktop/iOS bridges -- there is no
 * per-keystroke "is a text field focused" check here, matching [pollGlfwTextInput]'s own
 * always-polling shape. A keystroke that lands with no focused text field to consume it is
 * silently dropped the next [Input.updateSnapshot] clears the buffer, same as desktop.
 * `ctrlKey`/`metaKey`/`altKey` are excluded from the printable-character path so this doesn't
 * intercept browser/OS shortcuts (Cmd+R, Ctrl+C, ...). */
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

private fun currentCanvasSize(): Pair<Int, Int> {
    val density = currentWindowDensity()
    val width = (window.innerWidth * density).toInt().coerceAtLeast(1)
    val height = (window.innerHeight * density).toInt().coerceAtLeast(1)
    return width to height
}

private fun currentWindowDensity(): Double {
    val scale = window.devicePixelRatio
    return if (scale.isFinite() && scale > 0.0) scale else 1.0
}

private fun syncCanvasSize(canvas: HTMLCanvasElement, width: Int, height: Int) {
    canvas.width = width
    canvas.height = height
}

/**
 * Configures the canvas surface using the browser's own preferred format
 * ([WGPUContext.renderingContext]'s `textureFormat`, resolved once by wgpu4k's
 * `canvasContextRenderer()` from `navigator.gpu.getPreferredCanvasFormat()`) rather than a
 * hardcoded guess -- configuring with any other format forces WebGPU to insert an extra copy
 * on every present (Chrome's console warns about exactly this). Every render pipeline in this
 * module reads the same resolved format back via `SwapchainManager.imageFormatWebGpu`, so
 * this stays the single source of truth for what the swapchain (and anything built to match
 * it, like [com.awakekt.awake.webgpu.texture.OffscreenRenderTarget]) is configured with.
 */
private fun configureSurface(wgpuContext: WGPUContext) {
    wgpuContext.surface.configure(
        SurfaceConfiguration(
            device = wgpuContext.device,
            format = wgpuContext.renderingContext.textureFormat,
            usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
            alphaMode = CompositeAlphaMode.Opaque,
        ),
    )
}
