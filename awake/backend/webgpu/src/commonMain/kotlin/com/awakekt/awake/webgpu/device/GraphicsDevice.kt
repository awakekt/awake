/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.webgpu.WebGpuHandles
import io.ygdrasil.webgpu.GPUFeatureName
import io.ygdrasil.webgpu.WGPUContext

/**
 * Phase 2.5 milestone 2 slice 1 (see docs/mvp-plan.md): real wgpu4k implementation.
 *
 * Unlike the other three platforms, [create]'s [window] parameter here is a pre-resolved
 * [WGPUContext], not a raw canvas -- `canvasContextRenderer()`/`Surface.configure()` are
 * `suspend`, and `kotlinx.coroutines.runBlocking` does not exist on `wasmJs` at all (a
 * single-threaded JS event loop can't block), confirmed the hard way (the import itself
 * doesn't resolve). `create()` on the `expect` contract is not `suspend`, so the async
 * device/surface acquisition has to happen *before* this is called -- the caller resolves
 * a `WGPUContext` via `canvasContextRenderer()` + `surface.configure()` in its own
 * coroutine, then passes it here. This is the same "[window] means something different per
 * platform" pattern already in use (GLFW `Long` on desktop, `android.view.Surface` on
 * Android) -- `Any` was deliberately loose for exactly this kind of flexibility.
 *
 * `wgpuContext` is `internal`, not part of the `expect` contract -- only other wasmJsMain
 * actuals (RenderPipeline/Mesh/Renderer, all in this same source set) reach into it
 * directly, via [WebGpuHandles] for the individual object handles their own Long-typed
 * fields need.
 */
class GraphicsDevice {
    /** Vulkan-parity placeholder, always 0: WebGPU has no instance object to hold. */
    var instance: Long = 0

    /**
     * Vulkan-parity placeholder, always 0: WebGPU reports validation errors through
     * uncaptured-error callbacks, not a messenger object.
     */
    var debugUtilsMessenger: Long = 0

    /**
     * Vulkan-parity placeholder, always 0: the canvas surface lives inside the [WGPUContext] passed
     * to [create].
     */
    var surface: Long = 0

    /**
     * Vulkan-parity placeholder, always 0: adapter selection happened before the context was handed
     * in.
     */
    var physicalDevice: Long = 0

    /** Table id, in [WebGpuHandles], of the wgpu device registered by [create]; 0 until then. */
    var device: Long = 0

    /**
     * Vulkan-parity placeholder, always 0: the device's single queue is reached through the
     * context.
     */
    var graphicsQueue: Long = 0

    /**
     * Vulkan-parity placeholder, always 0: presentation goes through the canvas context, not a
     * queue.
     */
    var presentQueue: Long = 0

    /**
     * Hardware classification of the active adapter. Defaults to [GpuDeviceClass.Unknown] on WebGPU
     * when adapter info is not exposed synchronously.
     */
    var deviceClass: GpuDeviceClass = GpuDeviceClass.Unknown

    internal lateinit var wgpuContext: WGPUContext
        private set

    /**
     * Whether a render pipeline's `unclippedDepth` takes effect on this device: it was granted
     * `depth-clip-control`, and this platform's binding passes the flag through. The shadow depth
     * pass sets it when true, so a caster in front of a cascade's near plane is clamped onto it
     * instead of clipped away.
     */
    internal val unclippedDepth: Boolean
        get() = UNCLIPPED_DEPTH_REACHES_PIPELINES && GPUFeatureName.DepthClipControl in wgpuContext.device.features

    /**
     * Adopts a pre-resolved WebGPU context as this device and registers its device in
     * [WebGpuHandles].
     *
     * Not `suspend`: the asynchronous adapter, device and surface acquisition must already have
     * happened in the caller's own coroutine. Call once; this class takes ownership of the context
     * and closes it in [destroy].
     *
     * @param window The [WGPUContext], typed `Any` to match the cross-platform contract where the
     * window handle differs per platform.
     * @throws IllegalStateException If [window] is not a [WGPUContext].
     */
    fun create(window: Any) {
        val context = window as? WGPUContext
            ?: error(
                "GraphicsDevice.create expects a pre-resolved WGPUContext on wasmJs " +
                    "(canvasContextRenderer() + surface.configure() must run in the " +
                    "caller's own coroutine first -- see this class's doc comment), got $window",
            )
        // Before anything reads the format: every pipeline and target is built with it.
        wgpuContext = context.withDisplayEncodedColorFormat()
        device = WebGpuHandles.register(wgpuContext.device)
    }

    /**
     * Releases [device]'s entry in [WebGpuHandles] and closes the owned [WGPUContext] in order:
     * surface, device and its queue, adapter. Every resource created from the device must already be
     * closed. The context must not be used or closed again by the caller afterwards.
     */
    fun destroy() {
        WebGpuHandles.release(device)
        closeContext(wgpuContext)
    }
}
