// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu

import io.github.ronjunevaldoz.awake.webgpu.device.GraphicsDevice
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Proves the WebGPU backend can create a real GPU device on the desktop JVM.
 *
 * This target exists purely for testing -- production runs Vulkan on desktop and Android
 * (decision-log D26). Before it, WebGPU code was compile-checked and never executed outside a
 * browser, so every WebGPU change rested on symmetry-with-Vulkan and manual inspection. This is
 * the first test in the repo that runs WebGPU code at all.
 *
 * `glfwContextRenderer` hints `GLFW_VISIBLE = FALSE`, so no window appears; the context is a
 * hidden 1x1 surface. It still needs a display server, which is why this is a desktop test
 * rather than something that runs anywhere.
 *
 * **What this does not prove.** Desktop wgpu4k runs wgpu-native over Vulkan/Metal, so this
 * exercises Awake's WebGPU code path, NOT a browser's WebGPU implementation. Canvas sizing, JS
 * interop, wasm memory and browser driver behaviour stay uncovered and still need a browser
 * check.
 */
class WebGpuDesktopDeviceSmokeTest {

    @Test
    fun createsARealGpuDeviceOnDesktop() = runBlocking<Unit> {
        val context = glfwContextRenderer(width = SIZE, height = SIZE, title = "awake-headless")
        val graphicsDevice = GraphicsDevice()
        try {
            // GLFWContext wraps the WGPUContext GraphicsDevice actually takes -- the same
            // type wasmJs hands it from canvasContextRenderer().
            graphicsDevice.create(context.wgpuContext)

            // A registered handle, not a default-initialised zero: `GraphicsDevice.create`
            // registers the wgpu4k device with WebGpuHandles, and every mesh/pipeline/material
            // in this backend resolves its own handle through that registry. Zero here would
            // mean the native library loaded but nothing was actually created.
            assertTrue(
                graphicsDevice.device != 0L,
                "expected a registered device handle, got ${graphicsDevice.device}",
            )
            assertNotNull(context.wgpuContext.device, "wgpu4k should have produced a GPUDevice")
        } finally {
            graphicsDevice.destroy()
        }
    }

    private companion object {
        /** `glfwContextRenderer`'s own default. Nothing is presented, so the size is irrelevant
         * beyond needing to be a valid surface. */
        const val SIZE = 1
    }
}
