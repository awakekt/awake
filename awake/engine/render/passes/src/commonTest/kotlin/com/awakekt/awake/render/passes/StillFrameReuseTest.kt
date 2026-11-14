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
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A frame that reads what the last one read is handed back whole, where the backend allows it. */
class StillFrameReuseTest {
    private val mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }
    private val material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
    private val lens = Lens(eye = Vec3f(0f, 2f, 6f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 50f)
    private val scratch = ScenePassScratch()
    private val preparer = CountingPreparer(redraws = true)

    @Test
    fun aStillFrameIsHandedBackWithoutPreparingAnything() {
        val first = compile(props(time = 0f))
        val prepared = preparer.prepared

        val second = compile(props(time = 1.5f))

        assertSame(first, second, "time passed, but no draw reads the clock")
        assertTrue(scratch.reusedLastFrame)
        assertEquals(prepared, preparer.prepared, "nothing is prepared again")
    }

    @Test
    fun anythingADrawReadsPlansTheFrameAgain() {
        val base = compile(props())
        val moved = compile(props().also { it[1].model = Mat4().translate(2.5f, 0f, 0f) })
        assertNotSame(base, moved, "a prop moved")
        val swaying = compile(props(time = 2f).onEach { it.vertexAnimation = Vec3f(0.1f, 0f, 0f) })
        assertNotSame(moved, swaying, "it starts swaying")
        assertNotSame(swaying, compile(props(time = 3f).onEach { it.vertexAnimation = Vec3f(0.1f, 0f, 0f) }), "a swaying prop's clock moves")
        val fewer = compile(props().dropLast(1))
        assertFalse(scratch.reusedLastFrame, "one prop streamed out")
        lens.eye = Vec3f(0f, 2f, 7f)
        assertNotSame(fewer, compile(props().dropLast(1)), "the camera moved")
    }

    @Test
    fun aBackendThatCannotRedrawAFramePreparesEveryFrame() {
        val preparer = CountingPreparer(redraws = false)
        compile(props(), preparer)
        val prepared = preparer.prepared

        compile(props(), preparer)

        assertFalse(scratch.reusedLastFrame)
        assertEquals(prepared * 2, preparer.prepared)
    }

    private fun props(time: Float = 0f): List<RenderDrawCommand> = List(3) { index ->
        RenderDrawCommand(mesh, material, model = Mat4().translate(index * 2f, 0f, 0f), timeSeconds = time)
    }

    private fun compile(draws: List<RenderDrawCommand>, using: GpuDrawPreparer = preparer): GpuPassInput = ScenePassCompiler.compile(
        lens, draws, null, EnvironmentUniforms.Default, ClipSpace.Vulkan, 1f, null, using, emptyList(), ImagePassChain.Empty, null, scratch,
    )

    private class CountingPreparer(private val redraws: Boolean) : GpuDrawPreparer {
        var prepared = 0
        private val handle = object : PipelineHandle, MaterialBinding, BufferHandle {}

        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw {
            prepared += 1
            return GpuResolvedDraw(pipeline = handle, materialBinding = handle, vertexBuffer = handle, indexBuffer = null, elementCount = 3)
        }

        override fun canInstance(format: VertexFormat): Boolean = false

        override val redrawsPreparedFrames: Boolean get() = redraws
    }
}
