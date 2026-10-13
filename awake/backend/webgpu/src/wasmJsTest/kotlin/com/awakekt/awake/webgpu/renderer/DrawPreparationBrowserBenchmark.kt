/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.webgpu.application.WebGpuEngine
import com.awakekt.awake.webgpu.device.createCanvasGpuContext
import io.ygdrasil.webgpu.WGPUContext
import kotlinx.coroutines.test.runTest
import web.dom.document
import web.html.HTMLCanvasElement
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import com.awakekt.awake.render.renderer.Renderer as RenderRenderer

/**
 * Times the WebGPU backend's draw preparation over a few thousand distinct draws in a real browser,
 * where every upload crosses from wasm into JS.
 *
 * The draws are lit-and-shadowed and textured meshes, none instanceable, so each is prepared and
 * uploaded on its own, the shape of a large hand-built scene. It asserts only that every draw
 * resolves; the printed milliseconds are for comparing two builds on one machine.
 */
class DrawPreparationBrowserBenchmark {
    @Test
    fun preparesThousandsOfDistinctDraws() = runTest(timeout = 10.minutes) {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val errors = mutableListOf<String>()
        val engine = BenchmarkEngine()
        val renderer = engine.boot(createCanvasGpuContext(canvas, SIZE, SIZE) { errors += it.message })
        assertNull(engine.startupError)
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val litMesh = renderer.createMesh(cube(VertexFormat.PositionNormalColor))
        val texturedMesh = renderer.createMesh(cube(VertexFormat.PositionNormalColorUv))
        val litMaterials = List(MATERIALS) { renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total) }
        val texturedMaterials = List(MATERIALS) {
            renderer.createMaterial(texture = WHITE, uniformFloatCount = MaterialUniformLayouts.PbrTextured.total, pbrTextures = null)
        }
        val pbr = pbrMaterialFloats(metallic = 0f, roughness = 1f, baseColorFactor = Color.White, emissiveFactor = Color.Transparent)
        val draws = List(DRAWS) { index ->
            val textured = index % 2 == 1
            RenderDrawCommand(
                mesh = if (textured) texturedMesh else litMesh,
                // A material of its own per draw, as distinct authored objects have: nothing folds into instances.
                material = (if (textured) texturedMaterials else litMaterials)[index / 2 % MATERIALS],
                model = Mat4().apply {
                    data[12] = (index % GRID - GRID / 2) * SPACING
                    data[14] = -(index / GRID) * SPACING
                },
                extraUniformFloats = if (textured) pbr else NO_EXTRAS,
            )
        }
        val lens = Lens(eye = Vec3f(0f, 30f, 40f), center = Vec3f(0f, 0f, -60f), fovYRadians = 1f, near = 0.1f, far = 400f)
        val sun = SceneLight(direction = Vec3f(0.4f, 0.8f, 0.4f), color = Vec3f(1f, 1f, 1f))
        val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
        val preparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer
        var prepareMs = 0.0
        var uploadMs = 0.0
        var recordMs = 0.0
        var resolved = 0
        repeat(WARMUP_FRAMES + MEASURED_FRAMES) { frame ->
            val start = TimeSource.Monotonic.markNow()
            val pass = ScenePassCompiler.compile(lens, draws, light, clipSpace = renderer.clipSpace, aspect = 1f, drawPreparer = preparer)
            val prepared = start.elapsedNow()
            // Recording would write the staged uniforms first; written here, they are timed apart.
            (renderer as Renderer).flushUniformUploads()
            val uploaded = start.elapsedNow()
            renderer.renderToTexture(target, pass)
            val recorded = start.elapsedNow()
            if (frame >= WARMUP_FRAMES) {
                prepareMs += prepared.inWholeMicroseconds / MICROS_PER_MS
                uploadMs += (uploaded - prepared).inWholeMicroseconds / MICROS_PER_MS
                recordMs += (recorded - uploaded).inWholeMicroseconds / MICROS_PER_MS
            }
            resolved = pass.resolvedOpaqueDraws.size
        }
        println(
            "draw preparation (browser WebGPU): draws=$DRAWS resolved=$resolved " +
                "prepare=${perFrame(prepareMs)} upload=${perFrame(uploadMs)} record+submit=${perFrame(recordMs)} ms/frame",
        )
        assertEquals(DRAWS, resolved)
        assertEquals(emptyList(), errors)
        target.destroy()
        engine.destroy()
    }

    private class BenchmarkEngine :
        WebGpuEngine(
            AppSpecBuilder().apply { window { size(SIZE, SIZE) } }.build().createLifecycle(),
            PLAN,
        ) {
        fun boot(context: WGPUContext): RenderRenderer {
            create(context)
            return renderer
        }

        fun destroy() = dispose()
    }

    private fun perFrame(totalMs: Double): Double = (totalMs / MEASURED_FRAMES * 10).roundToInt() / 10.0

    private companion object {
        const val SIZE = 64
        const val DRAWS = 4_900
        const val MATERIALS = 2_450
        const val GRID = 70
        const val SPACING = 3f
        const val WARMUP_FRAMES = 10
        const val MEASURED_FRAMES = 30
        const val MICROS_PER_MS = 1_000.0
        val NO_EXTRAS = FloatArray(0)
        val WHITE = TextureAsset(data = byteArrayOf(-1, -1, -1, -1), width = 1, height = 1)

        val PLAN = RenderPlan(
            primary = ScenePipeline(PipelineKey.Primary, PackShaderSets.LitShadow, VertexFormat.PositionNormalColor, materialBindings = GroupBindings.UniformOnlyMaterial),
            depthPrePassShaderSet = PackShaderSets.ShadowDepth,
            sceneDepthShaderSet = PackShaderSets.SceneDepth,
            scenePipelines = listOf(
                ScenePipeline(PipelineKey.Format(VertexFormat.PositionNormalColorUv), PackShaderSets.Textured, VertexFormat.PositionNormalColorUv),
            ),
        )

        /** A unit cube in [format], every vertex white with an upward normal; the shading is not under test. */
        fun cube(format: VertexFormat): MeshGeometry {
            val corners = listOf(
                -1f to -1f,
                1f to -1f,
                1f to 1f,
                -1f to 1f,
            )
            val vertices = buildList {
                for (y in listOf(-0.5f, 0.5f)) {
                    corners.forEach { (x, z) ->
                        addAll(listOf(x * 0.5f, y, z * 0.5f, 0f, 1f, 0f, 1f, 1f, 1f))
                        if (format == VertexFormat.PositionNormalColorUv) addAll(listOf((x + 1f) / 2f, (z + 1f) / 2f))
                    }
                }
            }.toFloatArray()
            val indices = intArrayOf(
                0, 1, 2, 2, 3, 0, 4, 6, 5, 6, 4, 7,
                0, 4, 5, 5, 1, 0, 1, 5, 6, 6, 2, 1,
                2, 6, 7, 7, 3, 2, 3, 7, 4, 4, 0, 3,
            )
            return MeshGeometry(vertices, indices, format)
        }
    }
}
