/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.fullScreenTriangleCorner
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2dArray
import com.awakekt.awake.asset.shaderdsl.textureSampleArrayLevel
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.shaders.spec
import com.awakekt.awake.asset.shaders.stagesFor
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Array textures carry a mip chain per layer. Array layer 1 is a 2x2 black-and-white checker, so
 * its level 1 is a single texel averaging to grey; level 0 at a quarter point is a pure texel.
 */
class RendererHeadlessTextureArrayMipTest {

    @Test
    fun anArrayLayersSecondLevelHoldsItsOwnAverage() {
        val attached = runBlocking { shared().attacher.attachContentFeature(PROBE) }
        val pixels = try {
            shared().render(LENS)
        } finally {
            attached.detach()
        }

        val quarter = HeadlessContentAttachFixture.TARGET_SIZE / 4
        listOf(quarter to quarter, 3 * quarter to quarter, quarter to 3 * quarter).forEach { (x, y) ->
            val red = pixels[(y * HeadlessContentAttachFixture.TARGET_SIZE + x) * 4].toInt() and 0xFF
            assertTrue(
                abs(red - CHECKER_AVERAGE) <= TOLERANCE,
                "Level 1 of layer 1 at ($x, $y) was $red, not the checker's average $CHECKER_AVERAGE: " +
                    "the level is missing (so level 0's pure texel shows) or averaged in layer 0's black.",
            )
        }
    }

    /** Draws the probe over the whole target, sampling at the level the uniform names. */
    private class LevelProbeFeature(
        private val pipeline: com.awakekt.awake.render.command.PipelineHandle,
        private val uniforms: com.awakekt.awake.render.command.UniformBlock,
    ) : RenderFeature<RenderFrameContext> {
        override val pass = RenderPassSlot.Scene

        override fun recordCommands(context: RenderFrameContext) {
            uniforms.write(context.frameIndex) { put(SAMPLED_LEVEL, LEVEL_FIELD) }
            context.recorder.bindPipeline(pipeline)
            context.recorder.bindMaterial(BindingSemantic.Material, uniforms.binding(context.frameIndex))
            context.recorder.draw(FULLSCREEN_TRIANGLE_VERTICES, 1)
        }

        override fun destroy() = Unit
    }

    private companion object {
        const val CHECKER_AVERAGE = 127
        const val TOLERANCE = 3
        val SAMPLED_LEVEL = floatArrayOf(1f)
        const val FULLSCREEN_TRIANGLE_VERTICES = 3
        val MATERIAL_GROUP = BindingLayout.Standard.slot(BindingSemantic.Material)
        val LEVEL_FIELD = UniformField("level", GpuDataShape.Float)
        val PROBE_LAYOUT = UniformLayout(LEVEL_FIELD)
        val LENS = Lens(eye = Vec3f(0f, 0f, 1f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 10f)

        val ProbeShader: AslShaderDefinition = shader("array_mip_probe") {
            val u = uniformBlock("Uniforms", group = MATERIAL_GROUP, binding = 0)
            val level = u.fieldsFrom(PROBE_LAYOUT).value("level")
            val layers by texture2dArray(group = MATERIAL_GROUP, binding = 1)
            val layerSampler by sampler(group = MATERIAL_GROUP, binding = 2)
            val out = varyings("VertexOutput")
            val uv by out.varying(GpuDataShape.Vec2, location = 0)
            vertex {
                val corner = fullScreenTriangleCorner()
                out.position set vec4(corner, 0f.lit, 1f.lit)
                uv set vec2((corner.x + 1f.lit) * 0.5f.lit, (corner.y + 1f.lit) * 0.5f.lit)
            }
            fragment {
                colorOutput(textureSampleArrayLevel(layers, layerSampler, uv, 1u.lit, level))
            }
        }

        /** Layer 0 black; layer 1 a black-and-white checker. */
        val TWO_LAYERS = TextureAsset(
            ByteArray(2 * 2 * 4 * 2) { index ->
                val texel = (index % 16) / 4
                val white = index >= 16 && (texel == 0 || texel == 3)
                if (index % 4 == 3 || white) -1 else 0
            },
            2,
            2,
            layerCount = 2,
        )

        val PROBE = ContentFeatureSource { backend ->
            ContentFeature(
                name = "array_mip_probe",
                spec = aslShaderSet(ProbeShader).stagesFor(backend).spec(
                    vertexFormat = VertexFormat.None,
                    variant = PipelineVariant.Background,
                    uniforms = PROBE_LAYOUT,
                ),
                textures = mapOf(1 to TWO_LAYERS),
            ) { pipeline, uniforms, _ -> LevelProbeFeature(pipeline, uniforms) }
        }

        private var fixture: HeadlessContentAttachFixture? = null

        fun shared(): HeadlessContentAttachFixture = fixture ?: HeadlessContentAttachFixture.create().also { fixture = it }

        @AfterClass
        @JvmStatic
        fun releaseSharedRenderer() {
            fixture?.release()
            fixture = null
        }
    }
}
