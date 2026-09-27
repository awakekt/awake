/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.vulkan.renderer.Renderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Shadows off on a renderer's very first frame.
 *
 * The scene pass binds the shadow map whether or not a depth pass ran this frame, so every layer
 * has to be out of `UNDEFINED` before that first bind. Validation reports the violation when the
 * device is destroyed, which is what `release` does -- so the assertion is the teardown itself.
 */
class RendererHeadlessShadowsOffTest {
    @Test
    fun aFirstFrameWithShadowsOffStillInitialisesTheShadowMap() {
        val (renderer, release) = newHeadlessShadowRenderer(SIZE)
        try {
            val target = renderer.createRenderTarget(SIZE, SIZE)
            withShadowsOffScene(renderer) { frame ->
                renderer.renderToTexture(target, frame)
                runBlocking { renderer.readPixels(target) }
            }
            target.destroy()
        } finally {
            release()
        }
    }

    /** The same frame through `draw`, into the stand-in images `VulkanEngine` boots headless with. */
    @Test
    fun aFirstFrameWithShadowsOffDrawsThroughTheHeadlessPresentablePath() {
        val (renderer, release) = newHeadlessShadowRenderer(SIZE, presentable = true)
        try {
            withShadowsOffScene(renderer) { frame ->
                renderer.draw(frame)
                val pixels = runBlocking { renderer.readPresentedPixels() }.data
                val centre = (SIZE / 2 * SIZE + SIZE / 2) * BYTES_PER_PIXEL
                assertTrue(
                    (pixels[centre].toInt() and 0xFF) > 0,
                    "The cube at the centre should be lit, not the black clear colour.",
                )
            }
            renderer.waitIdle()
        } finally {
            release()
        }
    }

    private fun withShadowsOffScene(renderer: Renderer, render: (GpuPassInput) -> Unit) {
        val ground = renderer.createMesh(generate { plane(size = 10f) })
        val cube = renderer.createMesh(generate { cube(size = 1f) })
        val material = renderer.createMaterial(LitShadowUniformLayout)
        val camera = Lens(eye = Vec3f(0f, 5f, 10f), center = Vec3f(0f, 0.5f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)
        val sun = SceneLight(direction = Vec3f(0.4f, 0.8f, 0.4f), color = Vec3f(1f, 1f, 1f))
        render(
            ScenePassCompiler.compile(
                lens = camera,
                drawCalls = listOf(
                    RenderDrawCommand(ground, material),
                    RenderDrawCommand(cube, material, Mat4().translate(0f, 0.5f, 0f)),
                ),
                // Cascades fitted as usual; the environment is what turns shadows off.
                light = sun.copy(cascades = shadowCascadeUniforms(sun, camera, 1f, renderer.clipSpace)),
                environment = EnvironmentUniforms(shadowsEnabled = false),
                clipSpace = renderer.clipSpace,
                aspect = 1f,
                drawPreparer = renderer.gpuDrawPreparer,
            ),
        )
        cube.destroy()
        ground.destroy()
        material.destroy()
    }

    private companion object {
        const val SIZE = 64
        const val BYTES_PER_PIXEL = 4
    }
}
