/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.PointLight
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.renderer.UniformFields
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GpuSceneFrameResolverTest {
    private object Pipeline : PipelineHandle
    private object Binding : MaterialBinding
    private object Buffer : BufferHandle

    private val mesh = object : Mesh {
        override val format = VertexFormat.PositionColorUv
        override val sizeBytes = 12L
        override fun destroy() = Unit
    }
    private val material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    @Test
    fun resolverOutputUsesSharedOpaqueAndTransparentOrdering() {
        val frame = TestGpuSceneFrame(
            lens = Lens(
                eye = Vec3f(0f, 0f, 2f),
                center = Vec3f.ZERO,
                fovYRadians = 1f,
                near = 0.1f,
                far = 10f,
            ),
            drawCalls = listOf(
                RenderDrawCommand(mesh, material, transparent = true),
                RenderDrawCommand(mesh, material),
            ),
        )

        val input = frame.toPassInput(
            ClipSpace.OpenGl,
            1f,
            TestGpuDrawResolver { draw, index ->
                GpuResolvedDraw(
                    pipeline = Pipeline,
                    materialBinding = Binding,
                    vertexBuffer = Buffer,
                    indexBuffer = null,
                    elementCount = 3,
                    transparent = draw.transparent,
                    depthSortKey = index.toFloat(),
                )
            },
        )

        assertEquals(1, input.resolvedOpaqueDraws.size)
        assertEquals(1, input.resolvedTransparentDraws.size)
        assertEquals(true, input.resolvedPath)
        assertEquals(0f, input.resolvedTransparentDraws.single().depthSortKey)
    }

    @Test
    fun compilerExposesCullAndPbrPayloadToTheResolverBeforeBackendLowering() {
        val authoredExtra = floatArrayOf(0.15f, 0.3f, 0.6f, 1f)
        var captured: RenderDrawCommand? = null
        val frame = TestGpuSceneFrame(
            lens = Lens(
                eye = Vec3f(0f, 0f, 2f),
                center = Vec3f.ZERO,
                fovYRadians = 1f,
                near = 0.1f,
                far = 10f,
            ),
            drawCalls = listOf(
                RenderDrawCommand(mesh, material, cullMode = CullMode.Back, extraUniformFloats = authoredExtra),
            ),
        )

        frame.toPassInput(
            ClipSpace.OpenGl,
            1f,
            TestGpuDrawResolver { draw, _ ->
                captured = draw
                GpuResolvedDraw(
                    pipeline = Pipeline,
                    materialBinding = Binding,
                    vertexBuffer = Buffer,
                    indexBuffer = null,
                    elementCount = 3,
                )
            },
        )

        assertEquals(CullMode.Back, captured?.cullMode)
        assertContentEquals(authoredExtra, captured?.extraUniformFloats)
    }

    @Test
    fun resolvedPathCarriesResolvedDrawsIntoShadowPrePasses() {
        val light = SceneLight(
            direction = Vec3f(0f, -1f, 0f),
            color = Vec3f(1f, 1f, 1f),
            viewProjection = Mat4(),
        )
        val frame = TestGpuSceneFrame(
            lens = Lens(
                eye = Vec3f(0f, 0f, 2f),
                center = Vec3f.ZERO,
                fovYRadians = 1f,
                near = 0.1f,
                far = 10f,
            ),
            drawCalls = listOf(RenderDrawCommand(mesh, material)),
            light = light,
        )

        val input = frame.toPassInput(
            ClipSpace.OpenGl,
            1f,
            TestGpuDrawResolver { _, _ ->
                GpuResolvedDraw(
                    pipeline = Pipeline,
                    materialBinding = Binding,
                    vertexBuffer = Buffer,
                    indexBuffer = null,
                    elementCount = 3,
                )
            },
        )

        assertEquals(true, input.resolvedPath)
        assertEquals(input.resolvedOpaqueDraws, input.prePasses.single().resolvedDraws)
    }

    @Test
    fun legacyCommandPreservesAuthoredFeaturePayloads() {
        val extra = floatArrayOf(1f, 2f, 3f)
        val instances = listOf(Mat4(), Mat4())
        val palettes = listOf(floatArrayOf(4f, 5f), floatArrayOf(6f, 7f))
        val colors = listOf(com.awakekt.awake.core.math.Vec4(1f, 0f, 0f, 1f), com.awakekt.awake.core.math.Vec4(0f, 1f, 0f, 1f))
        val frames = listOf(2f, 5f)
        val draw = RenderDrawCommand(
            mesh = mesh,
            material = material,
            extraUniformFloats = extra,
            vertexAnimation = Vec3f(0.1f, 0.2f, 0.3f),
            timeSeconds = 4f,
            instanceModels = instances,
            instanceJointPalettes = palettes,
            instanceColors = colors,
            instanceFrames = frames,
            transparent = true,
        )

        assertContentEquals(extra, draw.extraUniformFloats)
        assertEquals(Vec3f(0.1f, 0.2f, 0.3f), draw.vertexAnimation)
        assertEquals(4f, draw.timeSeconds)
        assertSame(instances, draw.instanceModels)
        assertSame(palettes, draw.instanceJointPalettes)
        assertSame(colors, draw.instanceColors)
        assertSame(frames, draw.instanceFrames)
        assertEquals(true, draw.transparent)
    }

    @Test
    fun genericPacketCarriesAuthoredDirectionalLightBytes() {
        val frame = TestGpuSceneFrame(
            lens = Lens(
                eye = Vec3f(0f, 0f, 2f),
                center = Vec3f.ZERO,
                fovYRadians = 1f,
                near = 0.1f,
                far = 10f,
            ),
            drawCalls = emptyList(),
            light = SceneLight(
                direction = Vec3f(0.1f, 0.2f, 0.3f),
                color = Vec3f(0.4f, 0.5f, 0.6f),
            ),
        )

        val input = frame.toPassInput(ClipSpace.OpenGl, 1f)

        assertContentEquals(
            floatArrayOf(0.1f, 0.2f, 0.3f, 0f, 0.4f, 0.5f, 0.6f, 0f),
            input.passUniforms.copyOfRange(
                MaterialUniformLayouts.SceneLight.offsetOf(UniformFields.LightDirection),
                MaterialUniformLayouts.SceneLight.offsetOf(UniformFields.LightDirection) +
                    MaterialUniformLayouts.DirectionalLight.total,
            ),
        )
    }

    @Test
    fun genericPacketCarriesPointLightSlotsAfterDirectionalBlock() {
        val frame = TestGpuSceneFrame(
            lens = Lens(
                eye = Vec3f.ZERO,
                center = Vec3f(0f, 0f, -1f),
                fovYRadians = 1f,
                near = 0.1f,
                far = 10f,
            ),
            drawCalls = emptyList(),
            light = SceneLight(
                direction = Vec3f(0f, 1f, 0f),
                color = Vec3f(1f, 1f, 1f),
                points = listOf(
                    PointLight(
                        position = Vec3f(1f, 2f, 3f),
                        color = Vec3f(0.2f, 0.3f, 0.4f),
                        range = 7f,
                    ),
                ),
            ),
        )

        val input = frame.toPassInput(ClipSpace.OpenGl, 1f)

        assertContentEquals(
            floatArrayOf(1f, 2f, 3f, 7f),
            input.passUniforms.copyOfRange(
                MaterialUniformLayouts.SceneLight.offsetOf(UniformFields.PointLightPositions),
                MaterialUniformLayouts.SceneLight.offsetOf(UniformFields.PointLightPositions) +
                    UniformFields.PointLightPositions.floats / UniformFields.PointLightPositions.count,
            ),
        )
        assertContentEquals(
            floatArrayOf(0.2f, 0.3f, 0.4f, 0f),
            input.passUniforms.copyOfRange(
                MaterialUniformLayouts.SceneLight.offsetOf(UniformFields.PointLightColors),
                MaterialUniformLayouts.SceneLight.offsetOf(UniformFields.PointLightColors) +
                    UniformFields.PointLightColors.floats / UniformFields.PointLightColors.count,
            ),
        )
    }
}

/** Test-only compatibility adapter; production callers use [ScenePassCompiler] directly. */
private data class TestGpuSceneFrame(
    val lens: Lens,
    val drawCalls: List<RenderDrawCommand>,
    val light: SceneLight? = null,
    val environment: com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms =
        com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms.Default,
) {
    fun toPassInput(clipSpace: ClipSpace, aspect: Float): com.awakekt.awake.render.command.GpuPassInput =
        ScenePassCompiler.compile(lens, drawCalls, light, environment, clipSpace, aspect)

    fun toPassInput(
        clipSpace: ClipSpace,
        aspect: Float,
        resolver: TestGpuDrawResolver,
    ): com.awakekt.awake.render.command.GpuPassInput {
        val legacy = toPassInput(clipSpace, aspect)
        val resolved = com.awakekt.awake.render.command.compileDrawCalls(drawCalls, resolver::resolve)
        val resolvedOpaque = resolved.opaqueByPipeline.values.flatten()
        return legacy.copy(
            prePasses = legacy.prePasses.map { it.copy(resolvedDraws = resolvedOpaque) },
            resolvedOpaqueDraws = resolvedOpaque,
            resolvedTransparentDraws = resolved.transparent,
            resolvedPath = true,
        )
    }
}

/** Compatibility-only resolver kept local to this test while the production compiler stays
 * independent of the authored [RenderDrawCommand] type. */
private fun interface TestGpuDrawResolver {
    fun resolve(drawCall: RenderDrawCommand, sourceIndex: Int): GpuResolvedDraw?
}
