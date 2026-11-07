/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.CastingFeature
import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.Companion.DRAW
import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.Companion.FRAME
import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.Companion.spec
import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.FakePipeline
import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.RecordingGpu
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentDepthSource
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.renderer.UniformWriter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Features with one pipeline spec, on a backend that gives each its own group: one pipeline and
 * one depth pipeline between them, each feature its own block, and the last detach frees both.
 */
class SharedContentPipelineTest {

    private val events = mutableListOf<String>()
    private val gpu = GroupGpu(events)
    private val attacher = ContentFeatureAttacher(gpu)

    @Test
    fun featuresWithOneSpecShareOnePipelineAndDrawWithTheirOwnBlocks() = runTest {
        val blocks = mutableListOf<UniformBlock>()
        repeat(3) { index -> attacher.attachContentFeature(terrain("terrain-$index", blocks)) }

        attacher.beforeGeometry.recordCommands(FRAME)

        assertEquals(1, gpu.registry.specs.size, "one pipeline for three features of one spec")
        assertEquals(listOf("group terrain-0", "group terrain-1", "group terrain-2"), events.filter { it.startsWith("group") })
        assertEquals(listOf("record terrain-0", "record terrain-1", "record terrain-2"), events.filter { it.startsWith("record") })
        assertEquals(3, blocks.distinct().size, "each feature draws with its own uniform block")
    }

    @Test
    fun theDepthPipelineIsBuiltOnceAndEveryFeatureCasts() = runTest {
        repeat(3) { index -> attacher.attachContentFeature(terrain("terrain-$index")) }

        assertEquals(1, events.count { it == "build depth" })
        assertEquals(3, gpu.casts.size)
        gpu.casts.forEach { assertEquals(DRAW, it.depthDraw(0, GpuEnvironmentState.Default)) }
    }

    /** The middle one leaves; its neighbours still record and cast, and the pipeline stays. */
    @Test
    fun detachingOneLeavesTheOthersDrawing() = runTest {
        val features = List(3) { index -> attacher.attachContentFeature(terrain("terrain-$index")) }
        events.clear()

        features[1].detach()
        attacher.beforeGeometry.recordCommands(FRAME)

        assertEquals(listOf("awaitIdle", "destroy terrain-1", "release terrain-1", "uncast", "record terrain-0", "record terrain-2"), events)
        assertNotNull(gpu.registry[SHARED])
        assertEquals(2, gpu.casts.size)
    }

    @Test
    fun theLastDetachReleasesThePipelineAndItsDepthPipeline() = runTest {
        val features = List(3) { index -> attacher.attachContentFeature(terrain("terrain-$index")) }
        features[0].detach()
        features[2].detach()
        events.clear()

        features[1].detach()

        assertEquals(listOf("awaitIdle", "destroy terrain-1", "destroy depth", "destroyPipeline", "release terrain-1", "uncast"), events)
        assertNull(gpu.registry[SHARED])
        assertTrue(gpu.casts.isEmpty())
    }

    /** Registered afresh, a new pipeline: nothing of the freed one is reused. */
    @Test
    fun aSpecCanBeSharedAgainAfterItsLastFeatureLeft() = runTest {
        attacher.attachContentFeature(terrain("terrain-0")).detach()
        val first = gpu.registry[SHARED]

        attacher.attachContentFeature(terrain("terrain-1"))
        attacher.attachContentFeature(terrain("terrain-2"))

        assertNull(first)
        assertEquals(1, gpu.registry.specs.size)
        assertEquals(2, events.count { it == "build depth" })
    }

    @Test
    fun aFailedSecondBuildKeepsThePipelineOfTheFirst() = runTest {
        attacher.attachContentFeature(terrain("terrain-0"))
        events.clear()

        runCatching { attacher.attachContentFeature(terrain("terrain-1", fail = true)) }

        assertEquals(listOf("group terrain-1", "release terrain-1"), events)
        assertNotNull(gpu.registry[SHARED])
    }

    @Test
    fun releaseAllFreesTheSharedDepthPipeline() = runTest {
        repeat(2) { index -> attacher.attachContentFeature(terrain("terrain-$index")) }
        events.clear()

        attacher.releaseAll()

        assertEquals(listOf("release terrain-0", "uncast", "release terrain-1", "uncast", "destroy depth"), events)
    }

    private fun terrain(name: String, blocks: MutableList<UniformBlock>? = null, fail: Boolean = false) = ContentFeatureSource {
        ContentFeature(name = name, spec = SHARED, depth = DEPTH) { _, block, _ ->
            check(!fail) { "build failed" }
            blocks?.add(block)
            CastingFeature(name, events)
        }
    }

    /** [RecordingGpu], plus a group per feature and one depth pipeline per spec. */
    private class GroupGpu(private val events: MutableList<String>) : ContentFeatureGpu<FakePipeline> by RecordingGpu(events) {
        val casts = mutableListOf<ContentDepthSource>()

        override fun uploadGroup(pipeline: FakePipeline, feature: ContentFeature): ContentGroupUpload {
            events += "group ${feature.name}"
            return ContentGroupUpload(Block(), ContentUpload(null) { events += "release ${feature.name}" })
        }

        override suspend fun createDepthCaster(pipeline: FakePipeline, depth: PipelineSpec): ContentDepthCaster {
            events += "build depth"
            return object : ContentDepthCaster {
                override suspend fun cast(source: ContentDepthSource): ContentUpload {
                    casts += source
                    return ContentUpload(null) {
                        casts -= source
                        events += "uncast"
                    }
                }

                override fun destroy() {
                    events += "destroy depth"
                }
            }
        }
    }

    private class Block : UniformBlock {
        override fun binding(frameIndex: Int): MaterialBinding = error("not recorded in these tests")

        override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) = Unit
    }

    private companion object {
        val SHARED = spec("terrain")
        val DEPTH = spec("terrain-depth")
    }
}
