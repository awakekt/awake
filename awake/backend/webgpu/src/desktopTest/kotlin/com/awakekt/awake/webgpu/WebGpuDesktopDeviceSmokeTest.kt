/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.device.unormCounterpart
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    /**
     * The device draws into the UNORM counterpart of the sRGB format wgpu-native's surface lists
     * first, as Vulkan's targets are and a browser's canvas always is (#1011). Shaders write colour
     * that is already display-encoded, which an sRGB target would encode a second time: the
     * tone-mapping step then drifted 17/255 from its CPU twin (`PostProcessingFxaaParityTest`).
     */
    @Test
    fun drawsIntoAUnormFormatWhenTheSurfaceListsAnSrgbOneFirst() = runBlocking<Unit> {
        val context = glfwContextRenderer(width = SIZE, height = SIZE, title = "awake-headless")
        val surfaceFormat = context.wgpuContext.renderingContext.textureFormat
        val graphicsDevice = GraphicsDevice()
        try {
            graphicsDevice.create(context.wgpuContext)
            val adopted = graphicsDevice.wgpuContext.renderingContext.textureFormat
            println("surface lists $surfaceFormat first; the device draws into $adopted")
            assertEquals(surfaceFormat.unormCounterpart() ?: surfaceFormat, adopted)
            assertNull(adopted.unormCounterpart(), "the device still draws into the sRGB format $adopted")
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
