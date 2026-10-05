/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.command.UniformBlockOwner
import com.awakekt.awake.render.passes.ContentDepthSource
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.PipelineFactory
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentFeatureAttacherTest {

    private val events = mutableListOf<String>()
    private val gpu = RecordingGpu(events)
    private val attacher = ContentFeatureAttacher(gpu)

    @Test
    fun anAttachedFeatureRecordsInItsPaintSlot() = runTest {
        attacher.attachContentFeature(source("sky", ContentPaint.BeforeGeometry))
        attacher.attachContentFeature(source("fog", ContentPaint.AfterGeometry))

        attacher.beforeGeometry.recordCommands(FRAME)
        attacher.afterGeometry.recordCommands(FRAME)

        assertEquals(listOf("upload sky", "upload fog", "record sky", "record fog"), events)
        assertEquals(2, gpu.registry.specs.size)
    }

    /** Earlier frames may still read what detach frees, so the wait comes first. */
    @Test
    fun detachWaitsForIdleThenFreesEverything() = runTest {
        val sky = attacher.attachContentFeature(source("sky"))
        events.clear()

        sky.detach()
        attacher.beforeGeometry.recordCommands(FRAME)

        assertEquals(listOf("awaitIdle", "destroy sky", "destroyPipeline", "release sky"), events)
        assertTrue(gpu.registry.specs.isEmpty())
    }

    @Test
    fun aSecondDetachDoesNothing() = runTest {
        val sky = attacher.attachContentFeature(source("sky"))
        sky.detach()
        events.clear()

        sky.detach()

        assertTrue(events.isEmpty())
    }

    /** Freed and registered again, so the same feature can come back with the next scene. */
    @Test
    fun aDetachedFeatureCanBeAttachedAgain() = runTest {
        attacher.attachContentFeature(source("sky")).detach()

        attacher.attachContentFeature(source("sky"))

        assertNotNull(gpu.registry[spec("sky")])
    }

    @Test
    fun aFeatureSamplingSceneDepthIsRejected() = runTest {
        assertFailsWith<IllegalArgumentException> {
            attacher.attachContentFeature(source("fog", samplesSceneDepth = true))
        }
        assertTrue(gpu.registry.specs.isEmpty())
    }

    @Test
    fun aSecondFeatureWithTheSameSpecIsRejected() = runTest {
        attacher.attachContentFeature(source("sky"))

        assertFailsWith<IllegalArgumentException> { attacher.attachContentFeature(source("sky")) }
        assertEquals(listOf("upload sky"), events)
    }

    /** Nothing has recorded with a half-built feature, so its pipeline and uploads go straight away. */
    @Test
    fun aFailedBuildReleasesWhatItAllocated() = runTest {
        assertFailsWith<IllegalStateException> {
            attacher.attachContentFeature(source("sky", build = { error("build failed") }))
        }

        assertEquals(listOf("upload sky", "destroyPipeline", "release sky"), events)
        assertNull(gpu.registry[spec("sky")])
    }

    @Test
    fun releaseAllFreesTheUploadsOfEveryAttachedFeature() = runTest {
        attacher.attachContentFeature(source("sky"))
        attacher.attachContentFeature(source("fog", ContentPaint.AfterGeometry))
        events.clear()

        attacher.releaseAll()

        assertEquals(listOf("release sky", "release fog"), events)
    }

    /** Its depth draw goes to the depth pass only while content features draw, and leaves with it. */
    @Test
    fun aFeatureWithADepthPipelineCastsUntilDetached() = runTest {
        val terrain = attacher.attachContentFeature(source("terrain", depth = true, build = { CastingFeature("terrain", events) }))
        val caster = gpu.depthSources.single()

        assertEquals(DRAW, caster.depthDraw(0, GpuEnvironmentState.Default))
        assertNull(caster.depthDraw(0, GpuEnvironmentState(contentFeatures = false)))
        events.clear()
        terrain.detach()

        assertEquals(listOf("awaitIdle", "destroy terrain", "destroyPipeline", "release terrain", "release depth"), events)
        assertTrue(gpu.depthSources.isEmpty())
    }

    @Test
    fun aDepthPipelineNeedsAFeatureThatHandsOverADraw() = runTest {
        assertFailsWith<IllegalArgumentException> { attacher.attachContentFeature(source("terrain", depth = true)) }

        assertEquals(listOf("upload terrain", "destroyPipeline", "release terrain"), events)
        assertTrue(gpu.depthSources.isEmpty())
    }

    private fun source(
        name: String,
        paint: ContentPaint = ContentPaint.BeforeGeometry,
        samplesSceneDepth: Boolean = false,
        depth: Boolean = false,
        build: () -> RenderFeature<RenderFrameContext> = { RecordingFeature(name, events) },
    ) = ContentFeatureSource {
        ContentFeature(
            name = name,
            spec = spec(name),
            paint = paint,
            samplesSceneDepth = samplesSceneDepth,
            depth = if (depth) spec("$name-depth") else null,
        ) { _, _, _ -> build() }
    }

    private class CastingFeature(name: String, events: MutableList<String>) :
        RenderFeature<RenderFrameContext> by RecordingFeature(name, events),
        ContentDepthSource {
        override fun depthDraw(frameIndex: Int, environment: GpuEnvironmentState): PreparedDraw = DRAW
    }

    private class RecordingFeature(val name: String, val events: MutableList<String>) : RenderFeature<RenderFrameContext> {
        override val pass = RenderPassSlot.Scene

        override fun recordCommands(context: RenderFrameContext) {
            events += "record $name"
        }

        override fun destroy() {
            events += "destroy $name"
        }
    }

    private class FakePipeline : UniformBlockOwner {
        override val uniformBlock: UniformBlock = object : UniformBlock {
            override fun binding(frameIndex: Int): MaterialBinding = error("not recorded in these tests")

            override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) = Unit
        }
    }

    private class RecordingGpu(val events: MutableList<String>) : ContentFeatureGpu<FakePipeline> {
        override val backend = RenderBackend.Vulkan
        override val registry = PipelineRegistry(PipelineFactory { _, _ -> FakePipeline() })
        val depthSources = mutableListOf<ContentDepthSource>()

        override suspend fun addDepthCaster(pipeline: FakePipeline, depth: PipelineSpec, source: ContentDepthSource): ContentUpload {
            depthSources += source
            return ContentUpload(null) {
                depthSources -= source
                events += "release depth"
            }
        }

        override fun handle(pipeline: FakePipeline) = object : PipelineHandle {}

        override fun upload(pipeline: FakePipeline, feature: ContentFeature): ContentUpload {
            events += "upload ${feature.name}"
            return ContentUpload(null) { events += "release ${feature.name}" }
        }

        override fun destroyPipeline(pipeline: FakePipeline) {
            events += "destroyPipeline"
        }

        override fun awaitIdle() {
            events += "awaitIdle"
        }
    }

    private companion object {
        val DRAW = object : PreparedDraw {
            override val pipeline = object : PipelineHandle {}
            override val materialBinding = object : MaterialBinding {}
            override val vertexBuffer = null
            override val indexBuffer = null
            override val elementCount = 3
        }

        /** The fakes record without reading the frame. */
        val FRAME = object : RenderFrameContext {
            override val frameIndex = 0
            override val groupedDrawCalls = emptyMap<PipelineHandle, List<PreparedDraw>>()
            override val primaryPipeline: PipelineHandle get() = error("unused")
            override val viewProjection: Mat4 get() = error("unused")
            override val cameraEye: Vec3f get() = error("unused")
            override val surfaceWidth = 1
            override val surfaceHeight = 1
            override val recorder: CommandRecorder get() = error("unused")
        }

        fun spec(name: String) = PipelineSpec(
            vertexFormat = VertexFormat.PositionColorUv,
            vertexShader = ShaderSource.ResourcePath("$name.wgsl", "vertexMain"),
            fragmentShader = ShaderSource.ResourcePath("$name.wgsl", "fragmentMain"),
            uniforms = LAYOUT,
        )

        /** Shared, like a real feature's layout singleton: `UniformLayout` compares by identity. */
        val LAYOUT = UniformLayout(UniformField("value", GpuDataShape.Vec4))
    }
}
