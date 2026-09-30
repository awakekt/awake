/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

// --8<-- [start:asl-imports]
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
// --8<-- [end:asl-imports]
import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.asset.shaders.RenderCapabilities
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.asset.shaders.narrowedTo
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.PipelineVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The "Render plans and shaders" guide: a render plan built from the shader pack, and one ASL shader. */
class ShadersDocsSampleTest {

    @Test
    fun aRenderPlanRequestsOnePipelinePerDeclaredShape() {
        val plan = GameRenderPlan

        val keys = plan.toPipelineRequests(RenderBackend.Vulkan).map { it.key }
        assertEquals(listOf(PipelineKey.Primary, PipelineKey.Particle, PipelineKey.Content("skybox")), keys)
        assertEquals(keys, plan.toPipelineRequests(RenderBackend.WebGpu).map { it.key }, "one plan serves both backends")
    }

    @Test
    fun anAslShaderEmitsWgslAndBecomesAShaderSet() {
        // --8<-- [start:asl]
        val tint: AslShaderDefinition = shader("tint") {
            val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
            val mvp by uniforms.field(GpuDataShape.Mat4)
            val tintColor by uniforms.field(GpuDataShape.Vec4)

            val out = varyings("VertexOutput")
            val color by out.varying(GpuDataShape.Vec3, location = 0)

            vertex {
                val ins = inputsFrom(VertexFormat.PositionNormalColor)
                out.position set (mvp * vec4(ins.input(VertexSemantic.Position), 1f.lit))
                color set ins.input(VertexSemantic.Color)
            }

            fragment {
                colorOutput(vec4(color * tintColor.xyz, 1f.lit))
            }
        }
        val wgsl: String = tint.emitWgsl()
        val tintShaders: ShaderSet = aslShaderSet(tint)
        // --8<-- [end:asl]

        assertTrue("@vertex" in wgsl && "fn vertexMain" in wgsl, wgsl)
        assertTrue("@fragment" in wgsl && "fn fragmentMain" in wgsl, wgsl)
        assertEquals(tintShaders.vulkan.stages.keys, tintShaders.webGpu.stages.keys)
    }

    @Test
    fun aBackendNarrowsAPlanToWhatItCanRun() {
        // --8<-- [start:narrow]
        val reports = mutableListOf<String>()
        val narrowed = GameRenderPlan.narrowedTo(
            RenderCapabilities(RenderBackend.WebGpu, depthPrePass = false),
            report = reports::add,
        )
        // --8<-- [end:narrow]

        assertNull(narrowed.depthPrePassShaderSet, "no depth pre-pass, so no shadow maps")
        assertTrue(reports.isNotEmpty(), "every dropped pass is reported")
        assertEquals(GameRenderPlan.primary, narrowed.primary)
    }
}

// --8<-- [start:plan]
val GameRenderPlan = RenderPlan(
    // Every mesh without a pipeline of its own draws through this one.
    primary = ScenePipeline(
        key = PipelineKey.Primary,
        shaders = PackShaderSets.LitShadow,
        vertexFormat = VertexFormat.PositionNormalColor,
        materialBindings = GroupBindings.UniformOnlyMaterial,
    ),
    scenePipelines = listOf(
        ScenePipeline(
            key = PipelineKey.Particle,
            shaders = PackShaderSets.Particle,
            vertexFormat = VertexFormat.PositionUv,
            variant = PipelineVariant.AlphaBlendedParticle,
            materialBindings = GroupBindings.ParticleMaterial,
        ),
    ),
    // The gradient sky draws only when the plan carries it.
    contentFeatures = listOf(skyboxContentFeature(PackShaderSets.Skybox)),
    // Renders shadow maps; without it nothing casts a shadow.
    depthPrePassShaderSet = PackShaderSets.ShadowDepth,
)
// --8<-- [end:plan]
