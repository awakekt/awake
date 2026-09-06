/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.pipeline.BindingSemantic
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedSkyboxRenderFeatureTest {

    private class RecordingCommandRecorder : CommandRecorder {
        val calls = mutableListOf<String>()
        var boundPipeline: PipelineHandle? = null
        var boundMaterial: MaterialBinding? = null
        var drawnVertexCount: Int = 0

        override fun bindPipeline(pipeline: PipelineHandle) {
            calls += "bindPipeline"
            boundPipeline = pipeline
        }

        override fun bindMaterial(semantic: BindingSemantic, binding: MaterialBinding) {
            calls += "bindMaterial:$semantic"
            boundMaterial = binding
        }

        override fun bindVertexBuffer(binding: Int, buffer: BufferHandle) {
            calls += "bindVertexBuffer:$binding"
        }

        override fun bindIndexBuffer(buffer: BufferHandle) {
            calls += "bindIndexBuffer"
        }

        override fun draw(vertexCount: Int, instanceCount: Int) {
            calls += "draw:$vertexCount:$instanceCount"
            drawnVertexCount = vertexCount
        }

        override fun drawIndexed(indexCount: Int, instanceCount: Int) {
            calls += "drawIndexed:$indexCount:$instanceCount"
        }
    }

    private object FakePipeline : PipelineHandle
    private object FakeBinding : MaterialBinding

    @Test
    fun testSkyboxRecording() {
        val recorder = RecordingCommandRecorder()
        val feature = SharedSkyboxRenderFeature()

        feature.recordCommands(
            recorder = recorder,
            pipeline = FakePipeline,
            uniformBinding = FakeBinding,
        )

        assertEquals(listOf("bindPipeline", "bindMaterial:Material", "draw:3:1"), recorder.calls)
        assertEquals(FakePipeline, recorder.boundPipeline)
        assertEquals(FakeBinding, recorder.boundMaterial)
        assertEquals(3, recorder.drawnVertexCount)
    }
}
