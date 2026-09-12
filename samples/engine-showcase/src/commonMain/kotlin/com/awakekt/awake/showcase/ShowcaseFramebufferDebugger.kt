/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.capture.FramebufferAttachment
import com.awakekt.awake.render.capture.FramebufferAttachmentData
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * State owned by the showcase framebuffer inspector.
 *
 * The UI requests a capture; [ShowcaseFramebufferCaptureSystem] performs it after the normal scene infrastructure
 * has run. Keeping the request here means the retained UI remains synchronous and the genuinely
 * asynchronous WebGPU readback does not leak into Compose code.
 */
internal class ShowcaseFramebufferDebugger {
    var selectedAttachment: FramebufferAttachment = FramebufferAttachment.Color0
        private set
    var captureInFlight: Boolean = false
        private set
    var lastCapture: FramebufferAttachmentData? = null
        private set
    var error: String? = null
        private set
    var previewMaterial: Material? = null
        private set

    private var pendingRequest: CaptureRequest? = null
    private var selectionGeneration: Int = 0
    private val scope = CoroutineScope(
        Dispatchers.Unconfined +
            CoroutineExceptionHandler { _, throwable ->
                fail(throwable.message ?: throwable::class.simpleName ?: "Capture failed")
            },
    )

    fun select(attachment: FramebufferAttachment) {
        if (selectedAttachment != attachment) {
            selectionGeneration += 1
            lastCapture = null
            error = null
        }
        selectedAttachment = attachment
    }

    fun requestCapture() {
        pendingRequest = CaptureRequest(selectedAttachment, selectionGeneration)
        error = null
    }

    private fun takeRequest(): CaptureRequest? {
        if (captureInFlight || pendingRequest == null) return null
        val request = pendingRequest
        pendingRequest = null
        captureInFlight = true
        return request
    }

    private fun complete(renderer: Renderer, request: CaptureRequest, data: FramebufferAttachmentData) {
        if (request.generation != selectionGeneration || request.attachment != selectedAttachment) {
            captureInFlight = false
            return
        }
        if (previewMaterial != null) {
            // A capture can finish while the previous UI frame still references its material.
            // Vulkan owns explicit descriptor/buffer lifetimes, so wait before replacing it.
            renderer.waitIdle()
            previewMaterial?.destroy()
        }
        previewMaterial = null
        lastCapture = data
        error = if (data.available) null else data.reason
        if (data.available && data.attachment == FramebufferAttachment.Color0) {
            previewMaterial = renderer.createMaterial(texture = data.toTextureAsset())
        }
        captureInFlight = false
    }

    private fun fail(message: String) {
        captureInFlight = false
        error = message
    }

    @Suppress("TooGenericExceptionCaught")
    internal fun update(runtime: SceneAppLifecycleRuntime, width: Int, height: Int) {
        val request = takeRequest() ?: return
        scope.launch {
            try {
                complete(
                    runtime.renderer,
                    request,
                    runtime.readbackAttachment(width, height, request.attachment),
                )
            } catch (throwable: Throwable) {
                fail(throwable.message ?: throwable::class.simpleName ?: "Capture failed")
            }
        }
    }

    internal fun dispose(renderer: Renderer) {
        scope.cancel()
        renderer.waitIdle()
        previewMaterial?.destroy()
        previewMaterial = null
    }
}

private data class CaptureRequest(
    val attachment: FramebufferAttachment,
    val generation: Int,
)

/** Runs after the normal scene render systems, so a request observes the same world/camera state
 * that was just rendered to the window. */
internal class ShowcaseFramebufferCaptureSystem(
    private val runtime: SceneAppLifecycleRuntime,
    private val debugger: ShowcaseFramebufferDebugger,
) : System {
    override fun update(world: World, delta: Float) {
        debugger.update(runtime, CAPTURE_WIDTH, CAPTURE_HEIGHT)
    }
}

private const val CAPTURE_WIDTH = 480
private const val CAPTURE_HEIGHT = 270

private fun FramebufferAttachmentData.toTextureAsset(): TextureAsset {
    require(available) { "Cannot build a preview from unavailable ${attachment.name} data." }
    require(channels >= 3) { "${attachment.name} preview needs at least RGB channels." }
    val pixels = ByteArray(width * height * 4)
    var pixel = 0
    while (pixel < width * height) {
        val source = pixel * channels
        val destination = pixel * 4
        pixels[destination] = values[source].toRgbaByte()
        pixels[destination + 1] = values[source + 1].toRgbaByte()
        pixels[destination + 2] = values[source + 2].toRgbaByte()
        pixels[destination + 3] = if (channels >= 4) values[source + 3].toRgbaByte() else 255.toByte()
        pixel += 1
    }
    return TextureAsset(pixels, width, height)
}

private fun Float.toRgbaByte(): Byte = (coerceIn(0f, 1f) * 255f).roundToInt().toByte()
