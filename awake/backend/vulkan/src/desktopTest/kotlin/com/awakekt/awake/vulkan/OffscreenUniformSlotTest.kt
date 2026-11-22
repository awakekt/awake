/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Offscreen captures prepared through [com.awakekt.awake.vulkan.renderer.Renderer.offscreenDrawPreparer]
 * use the dedicated offscreen uniform slot (`commandBuffers.size`) rather than `currentFrame`
 * (#1687, #1709), so presenting the main scene in the same frame neither stalls the CPU on the
 * offscreen fence nor overwrites the offscreen capture's camera or draw uniforms.
 */
class OffscreenUniformSlotTest {

    @Test
    fun offscreenCapturesOnDedicatedSlotDoNotStallThePresentedFrameAndPreserveBothViews() {
        val (renderer, close) = newHeadlessShadowRenderer(SIZE, presentable = true)
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val mesh = renderer.createMesh(generate { cube(size = 1.6f) })
        val material = renderer.createMaterial(LitShadowUniformLayout)
        try {
            val light = SceneLight(direction = Vec3f(0.4f, 1f, 0.3f), color = Vec3f(1f, 1f, 1f))
            val offscreenEnv = EnvironmentUniforms(clearColor = Color(0f, 0f, 0.6f, 1f))
            val mainEnv = EnvironmentUniforms(clearColor = Color(0.6f, 0f, 0f, 1f))
            // Offscreen camera looks directly at the cube; main camera looks away into empty sky.
            val cubeCamera = Lens(eye = Vec3f(0f, 0f, 4f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 40f)
            val emptyCamera = Lens(eye = Vec3f(0f, 0f, 4f), center = Vec3f(0f, 0f, 12f), fovYRadians = 1f, near = 0.1f, far = 40f)
            val draw = listOf(RenderDrawCommand(mesh, material, model = Mat4()))

            repeat(FRAMES) {
                val offscreenPass = ScenePassCompiler.compile(
                    lens = cubeCamera,
                    drawCalls = draw,
                    light = light,
                    environment = offscreenEnv,
                    clipSpace = renderer.clipSpace,
                    aspect = 1f,
                    drawPreparer = renderer.offscreenDrawPreparer,
                )
                renderer.submitToTexture(target, offscreenPass)

                val mainPass = ScenePassCompiler.compile(
                    lens = emptyCamera,
                    drawCalls = draw,
                    light = light,
                    environment = mainEnv,
                    clipSpace = renderer.clipSpace,
                    aspect = 1f,
                    drawPreparer = renderer.gpuDrawPreparer,
                )
                renderer.draw(mainPass)
            }

            assertEquals(
                0L,
                renderer.offscreenWaitsForFrame,
                "presented frames must not block on an offscreen capture prepared on the dedicated offscreen slot",
            )
            renderer.waitIdle()
            val offscreenPixels = runBlocking { renderer.readPixels(target) }.data
            val mainPixels = runBlocking { renderer.readPresentedPixels() }.data
            assertFalse(offscreenPixels.contentEquals(mainPixels), "offscreen and main views render distinct scenes")
            // Centre pixel of offscreen target hits the lit cube (not pure blue clear colour).
            val centre = ((SIZE / 2) * SIZE + (SIZE / 2)) * 4
            val red = offscreenPixels[centre].toInt() and 0xFF
            val green = offscreenPixels[centre + 1].toInt() and 0xFF
            assertTrue(red > 40 && green > 40, "offscreen capture kept its own camera and drew the cube (r=$red g=$green)")
        } finally {
            renderer.waitIdle()
            mesh.destroy()
            material.destroy()
            target.destroy()
            close()
        }
    }

    @Test
    fun legacyOffscreenCaptureOnPrimaryPreparerStillWaitsBeforePresentedFrameWrites() {
        val (renderer, close) = newHeadlessShadowRenderer(SIZE, presentable = true)
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val mesh = renderer.createMesh(generate { cube(size = 1.6f) })
        val material = renderer.createMaterial(LitShadowUniformLayout)
        try {
            val light = SceneLight(direction = Vec3f(0.4f, 1f, 0.3f), color = Vec3f(1f, 1f, 1f))
            val camera = Lens(eye = Vec3f(0f, 0f, 4f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 40f)
            val draw = listOf(RenderDrawCommand(mesh, material, model = Mat4()))

            // Legacy caller compiles with gpuDrawPreparer (primary slot) and submits offscreen.
            val offscreenPass = ScenePassCompiler.compile(
                lens = camera,
                drawCalls = draw,
                light = light,
                clipSpace = renderer.clipSpace,
                aspect = 1f,
                drawPreparer = renderer.gpuDrawPreparer,
            )
            renderer.submitToTexture(target, offscreenPass)

            val mainPass = ScenePassCompiler.compile(
                lens = camera,
                drawCalls = draw,
                light = light,
                clipSpace = renderer.clipSpace,
                aspect = 1f,
                drawPreparer = renderer.gpuDrawPreparer,
            )
            renderer.draw(mainPass)

            assertTrue(
                renderer.offscreenWaitsForFrame > 0L,
                "a capture prepared on currentFrame must still wait before the presented frame rewrites currentFrame",
            )
        } finally {
            renderer.waitIdle()
            mesh.destroy()
            material.destroy()
            target.destroy()
            close()
        }
    }

    private companion object {
        const val SIZE = 64
        const val FRAMES = 6
    }
}
