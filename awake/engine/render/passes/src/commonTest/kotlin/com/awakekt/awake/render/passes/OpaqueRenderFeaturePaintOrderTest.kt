/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.pipeline.BindingSemantic
import kotlin.test.Test
import kotlin.test.assertEquals

class OpaqueRenderFeaturePaintOrderTest {
    private class NamedPipeline(val label: String) : PipelineHandle

    private class RecordingCommandRecorder(private val events: MutableList<String>) : CommandRecorder {
        override fun bindPipeline(pipeline: PipelineHandle) {
            events += "pipeline:${(pipeline as NamedPipeline).label}"
        }

        override fun bindMaterial(semantic: BindingSemantic, binding: MaterialBinding) = Unit
        override fun bindVertexBuffer(binding: Int, buffer: BufferHandle) = Unit
        override fun bindIndexBuffer(buffer: BufferHandle) = Unit
        override fun setScissor(x: Int, y: Int, width: Int, height: Int) = Unit
        override fun draw(vertexCount: Int, instanceCount: Int) {
            events += "draw:$vertexCount"
        }

        override fun drawIndexed(indexCount: Int, instanceCount: Int) {
            events += "drawIndexed:$indexCount"
        }
    }

    private fun markerFeature(label: String, events: MutableList<String>) = object : RenderFeature<RenderFrameContext> {
        override val pass = RenderPassSlot.Scene
        override fun recordCommands(context: RenderFrameContext) {
            events += "feature:$label"
        }

        override fun destroy() = Unit
    }

    private fun draw(pipeline: PipelineHandle): PreparedDraw = GpuResolvedDraw(
        pipeline = pipeline,
        materialBinding = object : MaterialBinding {},
        vertexBuffer = object : BufferHandle {},
        indexBuffer = null,
        elementCount = 3,
    )

    @Test
    fun recordsOpaqueMeshesBeforeTerrainThenAfterOpaqueSkyThenBeforeTransparentThenTransparent() {
        val events = mutableListOf<String>()
        val opaquePipeline = NamedPipeline("opaque")
        val transparentPipeline = NamedPipeline("transparent")
        val feature = OpaqueRenderFeature<RenderFrameContext>(
            lines = object : LinePass<RenderFrameContext> {
                override fun writeMvp(frameIndex: Int, mvp: FloatArray) = Unit
                override fun lineDraw(context: RenderFrameContext): PreparedDraw? = null
                override fun destroy() = Unit
            },
            beforeTransparent = listOf(markerFeature("water", events)),
            opaqueGeometry = listOf(markerFeature("terrain", events)),
            afterOpaque = listOf(markerFeature("sky", events)),
        )
        val context = object : RenderFrameContext {
            override val frameIndex = 0
            override val groupedDrawCalls = mapOf(opaquePipeline to listOf(draw(opaquePipeline)))
            override val transparentDrawCalls = listOf(draw(transparentPipeline))
            override val primaryPipeline = opaquePipeline
            override val viewProjection = Mat4()
            override val cameraEye = Vec3f.ZERO
            override val surfaceWidth = 64
            override val surfaceHeight = 64
            override val recorder = RecordingCommandRecorder(events)
        }

        feature.recordCommands(context)

        assertEquals(
            listOf(
                "pipeline:opaque",
                "draw:3",
                "feature:terrain",
                "feature:sky",
                "feature:water",
                "pipeline:transparent",
                "draw:3",
            ),
            events,
        )
    }
}
