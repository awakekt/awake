/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.RenderViewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScenePassCompilerTest {

    @Test
    fun drawPreparerProducesCanonicalResolvedPacket() {
        val mesh = object : Mesh {
            override val format = VertexFormat.PositionColorUv
            override val sizeBytes = 0L
            override fun destroy() = Unit
        }
        val material = object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        }
        val input = ScenePassCompiler.compile(
            lens = Lens(
                eye = Vec3f(0f, 0f, 1f),
                center = Vec3f.ZERO,
                fovYRadians = 1f,
                near = 0.1f,
                far = 10f,
            ),
            drawCalls = listOf(RenderDrawCommand(mesh, material)),
            clipSpace = ClipSpace.Vulkan,
            aspect = 1f,
            drawPreparer = com.awakekt.awake.render.command.GpuDrawPreparer { _, _, _ ->
                GpuResolvedDraw(
                    pipeline = object : PipelineHandle {},
                    materialBinding = object : MaterialBinding {},
                    vertexBuffer = object : BufferHandle {},
                    indexBuffer = null,
                    elementCount = 3,
                )
            },
        )

        assertTrue(input.resolvedPath)
        assertEquals(1, input.resolvedOpaqueDraws.size)
    }

    @Test
    fun drawPreparerReceivesCompiledFrameContext() {
        val mesh = object : Mesh {
            override val format = VertexFormat.PositionColorUv
            override val sizeBytes = 0L
            override fun destroy() = Unit
        }
        val material = object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        }
        var received: GpuDrawPreparationContext? = null
        val viewport = RenderViewport(4f, 8f, 320f, 180f)
        val input = ScenePassCompiler.compile(
            lens = Lens(
                eye = Vec3f(0f, 2f, 3f),
                center = Vec3f.ZERO,
                fovYRadians = 1f,
                near = 0.1f,
                far = 10f,
            ),
            drawCalls = listOf(RenderDrawCommand(mesh, material)),
            clipSpace = ClipSpace.Vulkan,
            aspect = viewport.aspect,
            viewport = viewport,
            drawPreparer = object : GpuDrawPreparer {
                override fun prepare(
                    request: RenderDrawCommand,
                    sourceIndex: Int,
                    context: GpuDrawPreparationContext,
                ): GpuResolvedDraw? {
                    received = context
                    return null
                }
            },
        )

        assertTrue(input.resolvedPath)
        assertEquals(Vec3f(0f, 2f, 3f), received?.cameraEye)
        assertEquals(viewport, received?.viewport)
        assertTrue(received?.passUniforms?.isNotEmpty() == true)
        val nonNullReceived = checkNotNull(received)
        assertEquals(input.environment, nonNullReceived.environment)
    }

    @Test
    fun resolverContextCarriesCompiledShadowMatricesOnlyWhenEnabled() {
        val mesh = object : Mesh {
            override val format = VertexFormat.PositionColorUv
            override val sizeBytes = 0L
            override fun destroy() = Unit
        }
        val material = object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        }
        var enabledMatrices = emptyList<com.awakekt.awake.core.math.Mat4>()
        val provider = object : com.awakekt.awake.render.command.GpuDrawPreparer {
            override fun prepare(
                request: RenderDrawCommand,
                sourceIndex: Int,
                context: GpuDrawPreparationContext,
            ): GpuResolvedDraw? {
                enabledMatrices = context.shadowViewProjections
                return null
            }
        }
        val lens = Lens(
            eye = Vec3f(0f, 1f, 2f),
            center = Vec3f.ZERO,
            fovYRadians = 1f,
            near = 0.1f,
            far = 10f,
        )
        val light = SceneLight(
            direction = Vec3f(0f, -1f, 0f),
            color = Vec3f(1f, 1f, 1f),
            viewProjection = com.awakekt.awake.core.math.Mat4(),
        )
        ScenePassCompiler.compile(
            lens = lens,
            drawCalls = listOf(RenderDrawCommand(mesh, material)),
            light = light,
            clipSpace = ClipSpace.Vulkan,
            aspect = 1f,
            drawPreparer = provider,
        )
        assertTrue(enabledMatrices.isNotEmpty())

        var disabledMatrices = listOf(com.awakekt.awake.core.math.Mat4())
        val disabledProvider = object : com.awakekt.awake.render.command.GpuDrawPreparer {
            override fun prepare(
                request: RenderDrawCommand,
                sourceIndex: Int,
                context: GpuDrawPreparationContext,
            ): GpuResolvedDraw? {
                disabledMatrices = context.shadowViewProjections
                return null
            }
        }
        ScenePassCompiler.compile(
            lens = lens,
            drawCalls = listOf(RenderDrawCommand(mesh, material)),
            light = light,
            environment = EnvironmentUniforms(shadowsEnabled = false),
            clipSpace = ClipSpace.Vulkan,
            aspect = 1f,
            drawPreparer = disabledProvider,
        )
        assertTrue(disabledMatrices.isEmpty())
    }

    @Test
    fun environmentStateSurvivesSceneCompilation() {
        val environment = EnvironmentUniforms(
            showSky = true,
            horizonColor = Color(0.1f, 0.2f, 0.3f, 1f),
            zenithColor = Color(0.4f, 0.5f, 0.6f, 1f),
            fogDensity = 0.25f,
            fogColor = Color(0.7f, 0.8f, 0.9f, 1f),
            shadowsEnabled = false,
        )
        val input = ScenePassCompiler.compile(
            lens = Lens(
                eye = Vec3f(0f, 0f, 1f),
                center = Vec3f(0f, 0f, 0f),
                fovYRadians = 1f,
                near = 0.1f,
                far = 10f,
            ),
            drawCalls = emptyList(),
            environment = environment,
            clipSpace = ClipSpace.Vulkan,
            aspect = 1f,
        )

        assertEquals(environment.toGpuState(), input.environment)
    }
}
