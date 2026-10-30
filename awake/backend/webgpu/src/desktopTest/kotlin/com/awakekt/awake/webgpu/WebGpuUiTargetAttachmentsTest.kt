/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.pipeline.MultisampleState
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.webgpu.texture.AttachmentAllocation
import com.awakekt.awake.webgpu.texture.OffscreenRenderTarget
import io.ygdrasil.webgpu.GPUTextureFormat
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * On the showcase plan at 4x, a target that only a UI pass draws into requests its 1x colour texture
 * alone; the first scene pass adds the 4x colour and 4x depth it renders into before resolving.
 *
 * Measured on wgpu-native over lavapipe at 128 x 128: one 1x texture after the UI pass, whose pixels
 * hold the UI quad, then the 4x colour and 4x `Depth32Float` after the scene pass: 65,536 bytes for
 * the UI layer instead of 589,824. Negative control: creating the scene attachments with the target,
 * as before, requests all three textures before any pass and fails the UI-only assertion.
 */
class WebGpuUiTargetAttachmentsTest {
    @Test
    fun uiOnlyTargetsRequestSingleSampleColourAlone() = runBlocking {
        val plan = EngineShowcaseRenderPlan.copy(multisample = MultisampleState(SAMPLES))
        webGpuHeadlessPlan(plan, surface = HeadlessSurface(SIZE, SIZE)).use { session ->
            val renderer = session.renderer
            val target = renderer.createRenderTarget(SIZE, SIZE) as OffscreenRenderTarget
            try {
                renderer.drawUiToTexture(target, listOf(UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color(1f, 0f, 0f, 1f))))
                val uiOnly = target.attachmentAllocations
                val red = renderer.readPixels(target).data
                println("UI-only 4x target: $uiOnly, ${uiOnly.sumOf(::bytes)} bytes")
                assertEquals(listOf(target.colorFormat to 1), uiOnly.map { it.format to it.samples }, "A UI layer holds its 1x colour alone")
                assertEquals(255, red[0].toInt() and 0xFF, "The UI pass drew into the 1x colour")

                val camera = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 10f)
                renderer.renderSceneToTexture(target, camera, emptyList(), SceneLight(direction = Vec3f(0f, 0f, 1f), color = Vec3f(1f, 1f, 1f)))
                val scene = target.attachmentAllocations
                println("Scene 4x target: $scene, ${scene.sumOf(::bytes)} bytes")
                assertEquals(
                    listOf(target.colorFormat to 1, target.colorFormat to SAMPLES, GPUTextureFormat.Depth32Float to SAMPLES),
                    scene.map { it.format to it.samples },
                    "A scene pass adds the multisampled colour and depth",
                )
            } finally {
                renderer.waitIdle()
                target.destroy()
            }
        }
    }

    /** Every format here takes four bytes a sample. */
    private fun bytes(allocation: AttachmentAllocation): Int =
        allocation.width * allocation.height * allocation.samples * BYTES_PER_SAMPLE

    private companion object {
        const val SIZE = 128
        const val SAMPLES = 4
        const val BYTES_PER_SAMPLE = 4
    }
}
