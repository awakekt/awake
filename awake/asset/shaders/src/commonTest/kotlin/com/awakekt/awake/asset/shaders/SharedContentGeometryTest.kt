/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.Companion.FRAME
import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.Companion.LAYOUT
import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.FakePipeline
import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.RecordingFeature
import com.awakekt.awake.asset.shaders.ContentFeatureAttacherTest.RecordingGpu
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.renderer.UniformWriter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Features declaring an equal [ContentFeature.geometryKey] share one uploaded GPU geometry per
 * [ContentFeatureAttacher], reference-counted until the last user detaches or [ContentFeatureAttacher.releaseAll] runs.
 */
class SharedContentGeometryTest {

    private val events = mutableListOf<String>()
    private val gpu = SharedGeometryGpu(events)
    private val attacher = ContentFeatureAttacher(gpu)

    @Test
    fun featuresWithSameGeometryKeyUploadGeometryOnceAcrossSpecs() = runTest {
        val seen = mutableListOf<ContentGeometry>()
        attacher.attachContentFeature(feature("terrain-a", SPEC_A, KEY_A, seen))
        attacher.attachContentFeature(feature("terrain-b", SPEC_A, KEY_A, seen))
        attacher.attachContentFeature(feature("terrain-c", SPEC_B, KEY_A, seen))

        attacher.beforeGeometry.recordCommands(FRAME)

        assertEquals(1, gpu.geometryUploads, "three features with one geometryKey share one GPU mesh upload")
        assertEquals(0, gpu.inlineGeometryUploads, "uploadGroup must not upload duplicate geometry when shared")
        assertEquals(0, gpu.geometryReleases)
        assertEquals(3, seen.size)
        assertSame(seen[0], seen[1])
        assertSame(seen[0], seen[2])
    }

    @Test
    fun detachingKeepsSharedGeometryAliveUntilTheLastUserDetaches() = runTest {
        val attachments = List(3) { index ->
            attacher.attachContentFeature(feature("terrain-$index", SPEC_A, KEY_A))
        }
        events.clear()

        attachments[0].detach()
        attachments[2].detach()
        assertEquals(0, gpu.geometryReleases, "shared geometry stays alive while one terrain remains attached")
        attacher.beforeGeometry.recordCommands(FRAME)
        assertTrue("record terrain-1" in events)

        events.clear()
        attachments[1].detach()
        assertEquals(1, gpu.geometryReleases, "detaching the last user releases the shared geometry")
        val idleIndex = events.indexOf("awaitIdle")
        val releaseIndex = events.indexOf("release geometry")
        assertTrue(idleIndex in 0 until releaseIndex, "awaitIdle must run before releasing shared GPU geometry: $events")
    }

    @Test
    fun reattachingAfterLastDetachUploadsFreshGeometry() = runTest {
        val firstSeen = mutableListOf<ContentGeometry>()
        attacher.attachContentFeature(feature("terrain-0", SPEC_A, KEY_A, firstSeen)).detach()

        val secondSeen = mutableListOf<ContentGeometry>()
        val second = attacher.attachContentFeature(feature("terrain-1", SPEC_A, KEY_A, secondSeen))

        assertEquals(2, gpu.geometryUploads)
        assertEquals(1, gpu.geometryReleases)
        assertNotSame(firstSeen.single(), secondSeen.single(), "a freed geometry must never be reused")

        second.detach()
        assertEquals(2, gpu.geometryReleases)
    }

    @Test
    fun distinctGeometryKeysUploadAndReleaseIndependently() = runTest {
        val a = attacher.attachContentFeature(feature("terrain-a", SPEC_A, KEY_A))
        val b = attacher.attachContentFeature(feature("terrain-b", SPEC_A, KEY_B))

        assertEquals(2, gpu.geometryUploads)
        assertEquals(0, gpu.geometryReleases)

        a.detach()
        assertEquals(1, gpu.geometryReleases)

        b.detach()
        assertEquals(2, gpu.geometryReleases)
    }

    @Test
    fun failedBuildsReleaseOrDecrementSharedGeometryCleanly() = runTest {
        assertFailsWith<IllegalStateException> {
            attacher.attachContentFeature(feature("failing-first", SPEC_A, KEY_A, failBuild = true))
        }
        assertEquals(1, gpu.geometryUploads)
        assertEquals(1, gpu.geometryReleases, "a failed first attach frees the shared geometry immediately")

        val surviving = attacher.attachContentFeature(feature("surviving", SPEC_A, KEY_A))
        assertFailsWith<IllegalStateException> {
            attacher.attachContentFeature(feature("failing-second", SPEC_A, KEY_A, failBuild = true))
        }
        assertEquals(2, gpu.geometryUploads)
        assertEquals(1, gpu.geometryReleases, "a failed second attach keeps the first feature's shared geometry alive")

        surviving.detach()
        assertEquals(2, gpu.geometryReleases)
    }

    @Test
    fun releaseAllFreesEachSharedGeometryExactlyOnce() = runTest {
        repeat(3) { index -> attacher.attachContentFeature(feature("a-$index", SPEC_A, KEY_A)) }
        repeat(2) { index -> attacher.attachContentFeature(feature("b-$index", SPEC_B, KEY_B)) }

        attacher.releaseAll()

        assertEquals(2, gpu.geometryUploads)
        assertEquals(2, gpu.geometryReleases)
    }

    private fun feature(
        name: String,
        spec: PipelineSpec,
        geometryKey: Any,
        seen: MutableList<ContentGeometry>? = null,
        failBuild: Boolean = false,
    ) = ContentFeatureSource {
        ContentFeature(
            name = name,
            spec = spec,
            geometry = QUAD,
            images = emptyMap(),
            alternateSpec = null,
            buildWithAlternate = null,
            geometryKey = geometryKey,
        ) { _, _, geometry ->
            check(!failBuild) { "build failed" }
            seen?.add(requireNotNull(geometry))
            RecordingFeature(name, events)
        }
    }

    private class SharedGeometryGpu(
        private val events: MutableList<String>,
    ) : ContentFeatureGpu<FakePipeline> by RecordingGpu(events) {
        var geometryUploads = 0
        var geometryReleases = 0
        var inlineGeometryUploads = 0

        override fun uploadGeometry(geometry: MeshGeometry): ContentUpload {
            geometryUploads++
            events += "upload geometry"
            val uploaded = ContentGeometry(object : BufferHandle {}, object : BufferHandle {}, geometry.indices.size)
            return ContentUpload(uploaded) {
                geometryReleases++
                events += "release geometry"
            }
        }

        override fun uploadGroup(pipeline: FakePipeline, feature: ContentFeature): ContentGroupUpload =
            uploadGroup(pipeline, feature, includeGeometry = true)

        override fun uploadGroup(
            pipeline: FakePipeline,
            feature: ContentFeature,
            includeGeometry: Boolean,
        ): ContentGroupUpload {
            events += "group ${feature.name}"
            val uploaded = if (includeGeometry && feature.geometry != null) {
                inlineGeometryUploads++
                ContentGeometry(object : BufferHandle {}, object : BufferHandle {}, feature.geometry!!.indices.size)
            } else {
                null
            }
            return ContentGroupUpload(Block(), ContentUpload(uploaded) { events += "release ${feature.name}" })
        }
    }

    private class Block : UniformBlock {
        override fun binding(frameIndex: Int): MaterialBinding = error("not recorded in these tests")

        override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) = Unit
    }

    private companion object {
        val SPEC_A = PipelineSpec(
            vertexFormat = VertexFormat.PositionColorUv,
            vertexShader = ShaderSource.ResourcePath("a.wgsl", "vertexMain"),
            fragmentShader = ShaderSource.ResourcePath("a.wgsl", "fragmentMain"),
            uniforms = LAYOUT,
        )
        val SPEC_B = PipelineSpec(
            vertexFormat = VertexFormat.PositionColorUv,
            vertexShader = ShaderSource.ResourcePath("b.wgsl", "vertexMain"),
            fragmentShader = ShaderSource.ResourcePath("b.wgsl", "fragmentMain"),
            uniforms = LAYOUT,
        )
        val QUAD = MeshGeometry(
            vertices = FloatArray(VertexFormat.PositionColorUv.strideFloats * 3),
            indices = intArrayOf(0, 1, 2),
            format = VertexFormat.PositionColorUv,
        )
        const val KEY_A = "clipmap-a"
        const val KEY_B = "clipmap-b"
    }
}
