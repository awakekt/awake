/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.COVERING_EXEMPT_MASK_LAYER
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An opaque draw exempt from the coverings is drawn into the mask's exempt layer, which distance fog
 * reads to leave an unlit surface as it drew (#1178), whatever outline layers the frame has; a
 * transparent one is not marked, and nothing else joins the layer.
 */
class CoveringExemptMaskTest {

    private val mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }
    private val material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    @Test
    fun anOpaqueExemptDrawAloneJoinsTheExemptLayer() {
        val drawsBySource = HashMap<Int, GpuResolvedDraw>()
        val input = ScenePassCompiler.compile(
            lens = Lens(eye = Vec3f(0f, 1f, 2f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 10f),
            drawCalls = listOf(
                RenderDrawCommand(mesh, material),
                RenderDrawCommand(mesh, material, exemptFromCoverings = true, maskLayer = 0),
                RenderDrawCommand(mesh, material, exemptFromCoverings = true, transparent = true),
            ),
            clipSpace = ClipSpace.Vulkan,
            aspect = 1f,
            drawPreparer = GpuDrawPreparer { request, sourceIndex, _ ->
                GpuResolvedDraw(
                    pipeline = object : PipelineHandle {},
                    materialBinding = object : MaterialBinding {},
                    vertexBuffer = object : BufferHandle {},
                    indexBuffer = null,
                    elementCount = 3,
                    transparent = request.transparent,
                ).also { drawsBySource[sourceIndex] = it }
            },
            maskLayers = listOf(floatArrayOf(1f, 0f, 0f, 1f, 3f)),
        )

        assertEquals(listOf(0, COVERING_EXEMPT_MASK_LAYER), input.maskPasses.map { it.targetLayer })
        val exempt = input.maskPasses.single { it.targetLayer == COVERING_EXEMPT_MASK_LAYER }
        assertEquals(listOf(drawsBySource.getValue(1)), exempt.resolvedDraws)
        assertEquals(listOf(drawsBySource.getValue(1)), input.maskPasses.first().resolvedDraws, "its outline layer still has it")
    }

    /** With no outline layers at all, an exempt draw still marks its pixels. */
    @Test
    fun theExemptLayerNeedsNoOutlineLayers() {
        val input = ScenePassCompiler.compile(
            lens = Lens(eye = Vec3f(0f, 1f, 2f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 10f),
            drawCalls = listOf(RenderDrawCommand(mesh, material, exemptFromCoverings = true)),
            clipSpace = ClipSpace.Vulkan,
            aspect = 1f,
            drawPreparer = GpuDrawPreparer { _, _, _ ->
                GpuResolvedDraw(
                    pipeline = object : PipelineHandle {},
                    materialBinding = object : MaterialBinding {},
                    vertexBuffer = object : BufferHandle {},
                    indexBuffer = null,
                    elementCount = 3,
                )
            },
        )

        assertEquals(listOf(COVERING_EXEMPT_MASK_LAYER), input.maskPasses.map { it.targetLayer })
    }
}
