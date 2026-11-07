/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.Companion.FRAME
import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.Companion.spec
import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.FakePipeline
import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.RecordingGpu
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.render.texture.TextureUploadRecorder
import com.sun.management.ThreadMXBean
import kotlinx.coroutines.runBlocking
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Features sharing a pipeline, each with its own group, are prepared and recorded every frame
 * without allocating: the slot walks them by index and the pass gate adds nothing.
 *
 * Negative control: recording the slot's list with `forEach` instead allocated its iterator,
 * 32 bytes per frame. Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class SharedContentFrameAllocationProbeTest {

    @Test
    fun threeFeaturesSharingAPipelineAllocateNothingPerFrame() {
        val attacher = ContentFeatureAttacher(GroupedGpu())
        runBlocking { repeat(FEATURES) { attacher.attachContentFeature(source()) } }
        val slot = attacher.beforeGeometry
        frames(slot, WARMUP_FRAMES)

        val start = allocated()
        frames(slot, MEASURED_FRAMES)

        assertEquals(0L, (allocated() - start) / MEASURED_FRAMES, "bytes per frame")
    }

    /** A plain loop: a lambda taking the frame index would box it, and count that. */
    private fun frames(slot: AttachedContentSlot, count: Int) {
        for (index in 0 until count) {
            slot.prepareFrame(index % 2, noUploads)
            slot.recordCommands(FRAME)
        }
    }

    private fun source() = ContentFeatureSource {
        ContentFeature(name = "terrain", spec = SHARED) { _, _, _ -> Quiet }
    }

    private object Quiet : RenderFeature<RenderFrameContext> {
        override val pass = RenderPassSlot.Scene

        override fun recordCommands(context: RenderFrameContext) = Unit

        override fun destroy() = Unit
    }

    private val noUploads = TextureUploadRecorder { _, _ -> }

    private class GroupedGpu : ContentFeatureGpu<FakePipeline> by RecordingGpu(mutableListOf()) {
        override fun uploadGroup(pipeline: FakePipeline, feature: ContentFeature) =
            ContentGroupUpload(Block, ContentUpload(null) {})
    }

    private object Block : UniformBlock {
        override fun binding(frameIndex: Int): MaterialBinding = error("unused")

        override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) = Unit
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        const val FEATURES = 3
        const val WARMUP_FRAMES = 300
        const val MEASURED_FRAMES = 600
        val SHARED = spec("terrain")
    }
}
