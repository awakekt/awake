/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.depthFogContentFeature
import com.awakekt.awake.asset.shaderpack.spriteScenePipeline
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.comparePixels
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies that a complete production [RenderPlan] boots through [HeadlessWebGpuEngine],
 * compiling all primary, depth pre-pass, scene depth, content and format pipelines dynamically,
 * and renders a lit, shadowed offscreen scene that matches a pixel baseline.
 *
 * Negative control (rule 3 of awake-render-headless-verification):
 * Inverting the directional light vector ([INVERTED_LIGHT_DIRECTION]) alters the lit face
 * distribution and shadow projection across the cube, diverging from the baseline in 672 pixels
 * (out of 16384 total pixels at 128x128).
 */
class WebGpuProductionPlanFrameTest {

    @Test
    fun productionPlanRendersLitCubeMatchingBaseline() = runBlocking {
        val record = System.getProperty("AWAKE_RECORD_SNAPSHOTS")?.toBoolean() == true
        val engine = HeadlessWebGpuEngine(plan = PRODUCTION_PLAN)
        val renderer = engine.boot(HeadlessSurface(TARGET_SIZE, TARGET_SIZE))
        try {
            val pixels = renderLitCube(renderer, LIGHT_DIRECTION)

            val baselineFile = resolveBaselineFile()
            if (record) {
                baselineFile.parentFile?.mkdirs()
                baselineFile.writeBytes(pixels)
                return@runBlocking
            }

            val baseline = if (baselineFile.exists()) {
                baselineFile.readBytes()
            } else {
                readResourceBytes(BASELINE_PATH)
            }

            val result = comparePixels(pixels, baseline)
            if (!result.matches) {
                val failureDir = File(FAILURE_DIR).apply { mkdirs() }
                val actualFile = File(failureDir, "actual.png")
                val expectedFile = File(failureDir, "baseline.png")
                writeRgbaPng(pixels, TARGET_SIZE, TARGET_SIZE, actualFile)
                writeRgbaPng(baseline, TARGET_SIZE, TARGET_SIZE, expectedFile)
                assertTrue(
                    false,
                    "WebGPU production plan render diverged from baseline: ${result.diffPixelCount} pixels differ " +
                        "(max channel diff ${result.maxChannelDiff}). See actual: ${actualFile.absolutePath}, " +
                        "baseline: ${expectedFile.absolutePath}",
                )
            }

            // Negative control check: inverting the light direction alters lit fragments.
            val invertedPixels = renderLitCube(renderer, INVERTED_LIGHT_DIRECTION)
            val negativeControl = comparePixels(invertedPixels, baseline)
            assertTrue(
                negativeControl.diffPixelCount > NEGATIVE_CONTROL_MIN_DIFF_PIXELS,
                "Negative control failed: inverting light direction must alter scene pixels, but diff was ${negativeControl.diffPixelCount}",
            )
        } finally {
            engine.destroy()
        }
    }

    private fun renderLitCube(renderer: Renderer, lightDirection: Vec3f): ByteArray = runBlocking {
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        val mesh = renderer.createMesh(MeshGeometry(CUBE_VERTICES, CUBE_INDICES, VertexFormat.PositionNormalColor))
        val material = renderer.createMaterial(uniformFloatCount = LitShadowUniformLayout.total)
        try {
            val camera = Lens(
                eye = Vec3f(2.5f, 2f, 4f),
                center = Vec3f(0f, 0f, 0f),
                fovYRadians = 1f,
                near = 0.1f,
                far = 10f,
            )
            val light = SceneLight(
                direction = lightDirection,
                color = Vec3f(1f, 1f, 1f),
            )
            val pass = ScenePassCompiler.compile(
                lens = camera,
                drawCalls = listOf(RenderDrawCommand(mesh, material)),
                light = light.copy(
                    cascades = shadowCascadeUniforms(
                        light,
                        camera,
                        renderer.surfaceAspect,
                        renderer.clipSpace,
                    ),
                ),
                clipSpace = renderer.clipSpace,
                aspect = renderer.surfaceAspect,
                drawPreparer = requireNotNull((renderer as? GpuDrawPreparationSource)?.gpuDrawPreparer),
            )
            renderer.renderToTexture(target, pass)
            renderer.readPixels(target).data
        } finally {
            mesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private fun resolveBaselineFile(): File {
        val rootRelative = File("awake/backend/webgpu/src/desktopTest/resources/$BASELINE_PATH")
        if (rootRelative.parentFile?.exists() == true || File("awake/backend/webgpu").exists()) {
            return rootRelative
        }
        return File("src/desktopTest/resources/$BASELINE_PATH")
    }

    private fun writeRgbaPng(pixels: ByteArray, width: Int, height: Int, file: File) {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        var offset = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = pixels[offset].toInt() and 0xFF
                val g = pixels[offset + 1].toInt() and 0xFF
                val b = pixels[offset + 2].toInt() and 0xFF
                val a = pixels[offset + 3].toInt() and 0xFF
                image.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
                offset += 4
            }
        }
        ImageIO.write(image, "png", file)
    }

    private companion object {
        const val TARGET_SIZE = 128
        const val BASELINE_PATH = "baselines/webgpu-production-plan.rgba"
        const val FAILURE_DIR = "build/test-failures/WebGpuProductionPlanFrameTest"
        const val NEGATIVE_CONTROL_MIN_DIFF_PIXELS = 100

        val LIGHT_DIRECTION = Vec3f(0.4f, 0.8f, 0.4f)
        val INVERTED_LIGHT_DIRECTION = Vec3f(-0.4f, -0.8f, -0.4f)

        val PRODUCTION_PLAN = RenderPlan(
            primary = ScenePipeline(
                PipelineKey.Primary,
                PackShaderSets.LitShadow,
                VertexFormat.PositionNormalColor,
                materialBindings = GroupBindings.UniformOnlyMaterial,
            ),
            depthPrePassShaderSet = PackShaderSets.ShadowDepth,
            sceneDepthShaderSet = PackShaderSets.SceneDepth,
            contentFeatures = listOf(
                depthFogContentFeature(Color(0.62f, 0.68f, 0.76f, 0.02f)),
            ),
            scenePipelines = listOf(
                spriteScenePipeline(),
                ScenePipeline(
                    PipelineKey.Format(VertexFormat.PositionNormalColorUv),
                    PackShaderSets.Textured,
                    VertexFormat.PositionNormalColorUv,
                ),
            ),
        )

        const val CORNER_NORMAL = 0.57735026f

        val CUBE_VERTICES = floatArrayOf(
            -0.5f, -0.5f, -0.5f, -CORNER_NORMAL, -CORNER_NORMAL, -CORNER_NORMAL, 0f, 0f, 0f,
            0.5f, -0.5f, -0.5f, CORNER_NORMAL, -CORNER_NORMAL, -CORNER_NORMAL, 1f, 0f, 0f,
            0.5f, 0.5f, -0.5f, CORNER_NORMAL, CORNER_NORMAL, -CORNER_NORMAL, 1f, 1f, 0f,
            -0.5f, 0.5f, -0.5f, -CORNER_NORMAL, CORNER_NORMAL, -CORNER_NORMAL, 0f, 1f, 0f,
            -0.5f, -0.5f, 0.5f, -CORNER_NORMAL, -CORNER_NORMAL, CORNER_NORMAL, 0f, 0f, 1f,
            0.5f, -0.5f, 0.5f, CORNER_NORMAL, -CORNER_NORMAL, CORNER_NORMAL, 1f, 0f, 1f,
            0.5f, 0.5f, 0.5f, CORNER_NORMAL, CORNER_NORMAL, CORNER_NORMAL, 1f, 1f, 1f,
            -0.5f, 0.5f, 0.5f, -CORNER_NORMAL, CORNER_NORMAL, CORNER_NORMAL, 0f, 1f, 1f,
        )

        val CUBE_INDICES = intArrayOf(
            0, 1, 2, 2, 3, 0,
            4, 5, 6, 6, 7, 4,
            0, 3, 7, 7, 4, 0,
            1, 5, 6, 6, 2, 1,
            0, 4, 5, 5, 1, 0,
            3, 2, 6, 6, 7, 3,
        )
    }
}
