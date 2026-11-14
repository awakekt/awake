/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Textured copies with their own tint and dither fade, folded into one instanced draw on a
 * `PipelineVariant.InstancedTinted` pipeline, draw exactly what the same copies draw one by one
 * through the textured pipeline (#1555). Untinted copies on it draw what they draw alone, too.
 *
 * Negative controls, measured: with the copy's fade multiplied into `exposure.y` (the share to
 * discard) rather than into what is left, the folded frame ignored every fade and differed from
 * the single draws in 1,350 bytes. Tints are asserted to show: the tinted folded frame differs
 * from the white one.
 */
class WebGpuInstanceTintParityTest {

    @Test
    fun tintedCopiesFoldedDrawWhatTheyDrawOneByOne() = withRenderer { renderer ->
        val tints = listOf(Color(1f, 0.2f, 0.2f), Color(0.2f, 1f, 0.2f), Color(0.3f, 0.4f, 1f))
        val fades = listOf(1f, 0.5f, 1f)

        val alone = renderer.frame(tints, fades, folded = false)
        val folded = renderer.frame(tints, fades, folded = true)

        assertEquals(1, folded.draws, "the three copies fold into one draw")
        assertEquals(3, alone.draws)
        assertContentEquals(alone.pixels, folded.pixels, "and draw the same pixels as alone")
        assertFalse(folded.pixels.contentEquals(renderer.frame(List(3) { Color.White }, List(3) { 1f }, folded = true).pixels), "tints show")
    }

    @Test
    fun untintedCopiesOnTheTintedPipelineDrawWhatTheyDrawAlone() = withRenderer { renderer ->
        val white = List(3) { Color.White }
        val opaque = List(3) { 1f }

        val folded = renderer.frame(white, opaque, folded = true)

        assertEquals(1, folded.draws)
        assertContentEquals(renderer.frame(white, opaque, folded = false).pixels, folded.pixels)
    }

    private class Frame(val draws: Int, val pixels: ByteArray)

    private fun Renderer.frame(tints: List<Color>, fades: List<Float>, folded: Boolean): Frame = runBlocking {
        val target = createRenderTarget(SIZE, SIZE)
        val mesh = createMesh(quad())
        val material = createMaterial(texture = WHITE, uniformFloatCount = MaterialUniformLayouts.PbrTextured.total, pbrTextures = PbrTextureSet())
        try {
            val draws = tints.indices.map { index ->
                RenderDrawCommand(
                    mesh = mesh,
                    material = material,
                    model = Mat4().translate((index - 1) * 2.2f, 0f, 0f),
                    extraUniformFloats = pbrMaterialFloats(metallic = 0f, roughness = 1f, baseColorFactor = tints[index], emissiveFactor = Color.Transparent),
                ).apply { fadeOpacity = fades[index] }
            }
            val real = requireNotNull((this@frame as GpuDrawPreparationSource).gpuDrawPreparer)
            val pass = ScenePassCompiler.compile(
                lens = Lens(eye = Vec3f(0f, 0f, 6f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 20f),
                drawCalls = draws,
                light = SceneLight(direction = Vec3f(0.3f, 0.6f, 1f), color = Vec3f(1f, 1f, 1f)),
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = if (folded) real else Alone(real),
            )
            renderToTexture(target, pass)
            Frame(pass.resolvedOpaqueDraws.size, readPixels(target).data)
        } finally {
            mesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    /** [real], refusing to instance anything, so every copy draws alone through the textured pipeline. */
    private class Alone(private val real: GpuDrawPreparer) : GpuDrawPreparer by real {
        override fun canInstance(format: VertexFormat): Boolean = false

        override fun canInstance(format: VertexFormat, cullMode: CullMode): Boolean = false
    }

    private fun <T> withRenderer(block: (Renderer) -> T): T {
        val engine = HeadlessWebGpuEngine(plan = PLAN)
        val renderer = runBlocking { engine.boot(HeadlessSurface(SIZE, SIZE)) }
        return try {
            block(renderer)
        } finally {
            engine.destroy()
        }
    }

    private companion object {
        const val SIZE = 96
        val WHITE = TextureAsset(data = ByteArray(4) { -1 }, width = 1, height = 1)

        val PLAN = RenderPlan(
            primary = ScenePipeline(PipelineKey.Primary, PackShaderSets.LitShadow, VertexFormat.PositionNormalColor, materialBindings = GroupBindings.UniformOnlyMaterial),
            depthPrePassShaderSet = PackShaderSets.ShadowDepth,
            sceneDepthShaderSet = PackShaderSets.SceneDepth,
            scenePipelines = listOf(
                ScenePipeline(PipelineKey.Format(VertexFormat.PositionNormalColorUv), PackShaderSets.Textured, VertexFormat.PositionNormalColorUv),
                ScenePipeline(
                    key = PipelineKey.InstancedFormat(VertexFormat.PositionNormalColorUv),
                    shaders = PackShaderSets.InstancedTexturedTinted,
                    vertexFormat = VertexFormat.PositionNormalColorUv,
                    variant = PipelineVariant.InstancedTinted,
                    depthShaders = PackShaderSets.InstancedTexturedShadowDepth,
                ),
            ),
        )

        /** A white square facing the camera, with UVs. */
        fun quad(): MeshGeometry {
            val corners = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)
            val vertices = corners.flatMap { (x, y) -> listOf(x, y, 0f, 0f, 0f, 1f, 1f, 1f, 1f, (x + 1f) / 2f, (y + 1f) / 2f) }
            return MeshGeometry(vertices.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionNormalColorUv)
        }
    }
}
