/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the showcase's production pipelines, including the camera-depth variant, with decorative
 * content disabled so a fog overlay cannot recolour this probe. Joint 1 moves each white quad
 * vertically; the models move them horizontally. Red and blue must follow the correct instance.
 * Measured on both backends: 676 pixels per tint, centroids (31, 82) and (95, 44),
 * red alpha 127. Bypassing the vertex tint multiplication and forcing alpha 1 produced
 * red=0, blue=0, white=1352 on both backends, failing both tests. Omitting tints after
 * a tinted draw restores those 1352 white pixels; zero joint palettes collapse both quads.
 */
class SkinnedInstanceTintParityTest {
    @Test
    fun vulkanTintsFollowPosedInstances() = verify(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuTintsFollowPosedInstances() = verify(HeadlessUiBackend.WebGpu)

    private fun verify(backend: HeadlessUiBackend) = runBlocking {
        open(backend).use { session ->
            val renderer = session.renderer
            val target = renderer.createRenderTarget(SIZE, SIZE)
            val mesh = renderer.createMesh(quad())
            val material = renderer.createMaterial(LitShadowUniformLayout)
            try {
                val lens = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 20f).apply {
                    projection = Lens.Projection.Orthographic
                    orthoHalfHeight = 2f
                }
                val models = listOf(Mat4().translate(-1f, 0f, 0f), Mat4().translate(1f, 0f, 0f))
                val palettes = listOf(palette(-0.6f), palette(0.6f))
                suspend fun render(colors: List<Vec4>?, joints: List<FloatArray> = palettes, shadowsEnabled: Boolean = false): ByteArray {
                    renderer.renderToTexture(
                        target,
                        ScenePassCompiler.compile(
                            lens = lens,
                            drawCalls = listOf(RenderDrawCommand(mesh, material, instanceModels = models, instanceJointPalettes = joints, instanceColors = colors)),
                            light = SceneLight(direction = Vec3f(0f, 0f, 1f), color = Vec3f(1f, 1f, 1f)),
                            environment = EnvironmentUniforms.Default.copy(shadowsEnabled = shadowsEnabled),
                            clipSpace = renderer.clipSpace,
                            aspect = 1f,
                            drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
                        ),
                    )
                    return renderer.readPixels(target).data
                }
                val tinted = counts(render(listOf(RED, BLUE)))
                println("$backend instanced skin tint: $tinted")
                assertTrue(tinted.red > 400 && tinted.blue > 400, "$backend: two distinct tints must draw: $tinted")
                assertTrue(tinted.redX < 50 && tinted.blueX > 78, "$backend: model indices must align with tints: $tinted")
                assertTrue(tinted.redY > 70 && tinted.blueY < 58, "$backend: the second palette's joint must keep its stride: $tinted")
                assertTrue(tinted.redAlpha in 125..130, "$backend: tint alpha must reach the attachment: $tinted")

                val withShadows = counts(render(listOf(RED, BLUE), shadowsEnabled = true))
                println("$backend instanced skin with shadows: $withShadows")
                assertTrue(withShadows.red > 400 && withShadows.blue > 400, "$backend: the shadow variant must preserve both palettes: $withShadows")

                val reversed = counts(render(listOf(BLUE, RED)))
                assertTrue(reversed.redX > 78 && reversed.blueX < 50, "$backend: swapping tints must swap colors: $reversed")
                val untinted = counts(render(null))
                println("$backend instanced skin default: $untinted")
                assertEquals(0, untinted.red + untinted.blue, "$backend: pooled tints must reset to white")
                assertTrue(untinted.white > 800, "$backend: omitted tints must preserve both quads: $untinted")
                val collapsed = counts(render(listOf(RED, BLUE), List(2) { FloatArray(32) }))
                assertEquals(0, collapsed.red + collapsed.blue, "$backend: zero palettes must collapse both quads")
            } finally {
                renderer.waitIdle()
                mesh.destroy()
                material.destroy()
                target.destroy()
            }
        }
    }

    private suspend fun open(backend: HeadlessUiBackend): HeadlessRenderSession {
        val plan = EngineShowcaseRenderPlan.copy(contentFeatures = emptyList())
        if (backend == HeadlessUiBackend.WebGpu) return webGpuHeadlessPlan(plan, surface = HeadlessSurface(SIZE, SIZE))
        val ready = CompletableDeferred<Renderer>()
        val app = AppSpecBuilder().apply { ready { ready.complete(it) } }.build().createLifecycle()
        val engine = VulkanEngine(app, plan)
        engine.create(HeadlessSurface(SIZE, SIZE))
        val renderer = withTimeout(30_000) { ready.await() }
        return object : HeadlessRenderSession {
            override val renderer = renderer
            override fun close() = engine.dispose()
        }
    }

    private fun palette(y: Float): FloatArray = Mat4().data + Mat4().translate(0f, y, 0f).data

    private fun quad() = MeshGeometry(
        floatArrayOf(
            -0.4f, -0.4f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, Float.fromBits(1), 0f, 0f, 0f, 1f, 0f, 0f,
            0.4f, -0.4f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, Float.fromBits(1), 0f, 0f, 0f, 1f, 0f, 0f,
            0.4f, 0.4f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, Float.fromBits(1), 0f, 0f, 0f, 1f, 0f, 0f,
            -0.4f, 0.4f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, Float.fromBits(1), 0f, 0f, 0f, 1f, 0f, 0f,
        ),
        intArrayOf(0, 1, 2, 2, 3, 0),
        VertexFormat.PositionNormalColorSkin,
    )

    private fun counts(pixels: ByteArray): Counts {
        var red = 0
        var blue = 0
        var white = 0
        var redX = 0
        var redY = 0
        var blueX = 0
        var blueY = 0
        var redAlpha = 0
        for (pixel in 0 until SIZE * SIZE) {
            val r = pixels[pixel * 4].toInt() and 255
            val g = pixels[pixel * 4 + 1].toInt() and 255
            val b = pixels[pixel * 4 + 2].toInt() and 255
            if (r > 100 && r > g + 50 && r > b + 50) {
                red++
                redX += pixel % SIZE
                redY += pixel / SIZE
                redAlpha += pixels[pixel * 4 + 3].toInt() and 255
            } else if (b > 100 && b > r + 50 && b > g + 50) {
                blue++
                blueX += pixel % SIZE
                blueY += pixel / SIZE
            } else if (r > 100 && g > 100 && b > 100) {
                white++
            }
        }
        return Counts(red, blue, white, redX / maxOf(1, red), redY / maxOf(1, red), blueX / maxOf(1, blue), blueY / maxOf(1, blue), redAlpha / maxOf(1, red))
    }

    private data class Counts(val red: Int, val blue: Int, val white: Int, val redX: Int, val redY: Int, val blueX: Int, val blueY: Int, val redAlpha: Int)

    private companion object {
        const val SIZE = 128
        val RED = Vec4(1f, 0f, 0f, 0.5f)
        val BLUE = Vec4(0f, 0f, 1f, 1f)
    }
}
