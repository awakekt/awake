/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.error.AppError
import com.awakekt.awake.engine.platform.error.AppErrorHandler
import com.awakekt.awake.engine.platform.error.AppErrorSource
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.webgpu.application.WebGpuDeviceLost
import com.awakekt.awake.webgpu.application.WebGpuDeviceLostException
import com.awakekt.awake.webgpu.application.WebGpuDeviceLostReason
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A booted WebGPU engine over wgpu-native is told its device was lost, as the browser host tells it
 * when `GPUDevice.lost` resolves. wgpu-native has no browser `lost` promise, so the loss is
 * simulated at the engine's entry point; the device itself stays valid, which lets the test tear the
 * engine down normally afterwards.
 */
class WebGpuDeviceLostTest {
    @Test
    fun aLostDeviceReachesTheHostOnceWithItsReasonAndNoFrameIsSubmittedAfterIt() = runBlocking {
        val errors = mutableListOf<AppError>()
        var frames = 0
        val lifecycle = AppSpecBuilder().apply {
            window { size(SIZE, SIZE) }
            service(AppErrorHandler::class, AppErrorHandler { errors += it })
            render { frames++ }
        }.build().createLifecycle()
        val engine = HeadlessWebGpuEngine(lifecycle, PLAN)
        val reported = mutableListOf<WebGpuDeviceLost>()
        engine.onDeviceLost = { reported += it }
        try {
            engine.boot(HeadlessSurface(SIZE, SIZE))
            engine.update(FRAME)
            assertEquals(1, frames, "a ready engine plays frames")
            val loss = WebGpuDeviceLost(WebGpuDeviceLostReason.Unknown, "GPU process exited")

            assertTrue(engine.deviceLost(loss))
            assertFalse(engine.deviceLost(loss.copy(message = "again")), "a second loss is not reported")
            repeat(3) { engine.update(FRAME) }

            assertEquals(listOf(loss), reported, "the host hears of the loss once, with its reason")
            val error = errors.single()
            assertEquals(AppErrorSource.GraphicsDevice, error.source)
            assertSame(loss, assertIs<WebGpuDeviceLostException>(error.cause).loss)
            assertNull(error.retry)
            assertEquals(1, frames, "no frame runs, so the renderer submits nothing to the lost device")
        } finally {
            engine.destroy()
        }
    }

    @Test
    fun theLossTheEnginesOwnTeardownCausesIsNotReported() = runBlocking {
        val engine = HeadlessWebGpuEngine(plan = PLAN)
        val reported = mutableListOf<WebGpuDeviceLost>()
        engine.onDeviceLost = { reported += it }
        engine.boot(HeadlessSurface(SIZE, SIZE))
        engine.destroy()

        val destroyed = WebGpuDeviceLost(WebGpuDeviceLostReason.Destroyed, "Device was destroyed.")
        assertFalse(engine.deviceLost(destroyed), "destroying the device on dispose resolves `lost`, which is no loss")
        assertTrue(reported.isEmpty())
        assertNull(engine.deviceLoss)
    }

    @Test
    fun theBrowsersReasonStringsMapToReasons() {
        assertEquals(WebGpuDeviceLostReason.Destroyed, WebGpuDeviceLostReason.of("destroyed"))
        assertEquals(WebGpuDeviceLostReason.Unknown, WebGpuDeviceLostReason.of("unknown"))
        assertEquals(WebGpuDeviceLostReason.Unknown, WebGpuDeviceLostReason.of("a reason from a newer spec"))
    }

    private companion object {
        const val SIZE = 8
        const val FRAME = 1f / 60f

        val PLAN = RenderPlan(
            primary = ScenePipeline(
                PipelineKey.Primary,
                PackShaderSets.LitShadow,
                VertexFormat.PositionNormalColor,
                materialBindings = GroupBindings.UniformOnlyMaterial,
            ),
        )
    }
}
