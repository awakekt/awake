/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.renderer.assertStagedUniformsMatchDirectWrites
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/** The staged uniform path over wgpu-native; see [assertStagedUniformsMatchDirectWrites]. */
class WebGpuUniformUploadsTest {
    @Test
    fun stagedBlocksWriteTheBytesDirectWritesDid() = runBlocking {
        val context = glfwContextRenderer(width = 1, height = 1, title = "awake-uniform-uploads")
        val graphicsDevice = GraphicsDevice().apply { create(context.wgpuContext) }
        try {
            context.wgpuContext.device.assertStagedUniformsMatchDirectWrites()
        } finally {
            graphicsDevice.destroy()
        }
    }
}
