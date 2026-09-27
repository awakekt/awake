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
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

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
            val ground = renderer.createMesh(generate { plane(size = 10f) })
            val cube = renderer.createMesh(generate { cube(size = 1f) })
            val material = renderer.createMaterial(LitShadowUniformLayout)
            val camera = Lens(eye = Vec3f(0f, 5f, 10f), center = Vec3f(0f, 0.5f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)
            val sun = SceneLight(direction = Vec3f(0.4f, 0.8f, 0.4f), color = Vec3f(1f, 1f, 1f))
            renderer.renderToTexture(
                target,
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
            runBlocking { renderer.readPixels(target) }
            cube.destroy()
            ground.destroy()
            material.destroy()
            target.destroy()
        } finally {
            release()
        }
    }

    private companion object {
        const val SIZE = 64
    }
}
