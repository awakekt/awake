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
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.pipeline.ResourceBinding
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.pipeline.ShaderStage
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Verifies refcounted [ContentFeature.textureKeys] sharing across attached content features,
 * including `mipSkip` cache partitioning, detach ordering after `awaitIdle`, and failure rollback.
 */
class SharedContentTextureTest {

    private val events = mutableListOf<String>()
    private val gpu = SharedTextureGpu(events)
    private val attacher = ContentFeatureAttacher(gpu)

    @Test
    fun featuresWithSameTextureKeyAndMipSkipShareOneUploadedTexture() = runTest {
        val a = attacher.attachContentFeature(feature("chunk-a", KEY_PALETTE_A))
        val b = attacher.attachContentFeature(feature("chunk-b", KEY_PALETTE_A))
        val c = attacher.attachContentFeature(feature("chunk-c", KEY_PALETTE_A))

        assertEquals(1, gpu.sharedTextureUploads, "three chunks sharing one palette key upload one GPU array")
        assertEquals(0, gpu.sharedTextureReleases)
        assertSame(gpu.boundHandles["chunk-a"], gpu.boundHandles["chunk-b"])
        assertSame(gpu.boundHandles["chunk-a"], gpu.boundHandles["chunk-c"])

        a.detach()
        c.detach()
        assertEquals(0, gpu.sharedTextureReleases, "shared texture stays resident while one chunk remains attached")
        attacher.beforeGeometry.recordCommands(FRAME)
        assertTrue("record chunk-b" in events)

        events.clear()
        b.detach()
        assertEquals(1, gpu.sharedTextureReleases, "last detach frees the shared GPU texture")
        val idleIndex = events.indexOf("awaitIdle")
        val releaseIndex = events.indexOf("release shared-texture")
        assertTrue(idleIndex in 0 until releaseIndex, "awaitIdle must precede shared texture release: $events")
    }

    @Test
    fun changingMipSkipPartitionsSharedTextureCacheWithoutStaleReuse() = runTest {
        gpu.currentMipSkip = 2
        val lowMip = attacher.attachContentFeature(feature("chunk-low", KEY_PALETTE_A))
        gpu.currentMipSkip = 0
        val fullMip = attacher.attachContentFeature(feature("chunk-full", KEY_PALETTE_A))

        assertEquals(2, gpu.sharedTextureUploads)
        assertNotSame(gpu.boundHandles["chunk-low"], gpu.boundHandles["chunk-full"])

        lowMip.detach()
        assertEquals(1, gpu.sharedTextureReleases)
        fullMip.detach()
        assertEquals(2, gpu.sharedTextureReleases)
    }

    @Test
    fun failedBuildsRollbackAndReleaseAllFreesOnce() = runTest {
        assertFailsWith<IllegalStateException> {
            attacher.attachContentFeature(feature("bad-first", KEY_PALETTE_A, failBuild = true))
        }
        assertEquals(1, gpu.sharedTextureUploads)
        assertEquals(1, gpu.sharedTextureReleases)

        attacher.attachContentFeature(feature("keep-a", KEY_PALETTE_A))
        attacher.attachContentFeature(feature("keep-b", KEY_PALETTE_A))
        attacher.releaseAll()
        assertEquals(2, gpu.sharedTextureUploads)
        assertEquals(2, gpu.sharedTextureReleases)
    }

    private fun feature(
        name: String,
        textureKey: Any,
        failBuild: Boolean = false,
    ) = ContentFeatureSource {
        ContentFeature(
            name = name,
            spec = SPEC,
            textures = mapOf(ARRAY_BINDING to ARRAY_TEXTURE),
            images = emptyMap(),
            alternateSpec = null,
            buildWithAlternate = null,
            geometryKey = null,
            textureKeys = mapOf(ARRAY_BINDING to textureKey),
        ) { _, _, _ ->
            check(!failBuild) { "build failed" }
            RecordingFeature(name, events)
        }
    }

    private class FakeHandle : ContentTextureHandle

    private class SharedTextureGpu(
        private val events: MutableList<String>,
    ) : ContentFeatureGpu<FakePipeline> by RecordingGpu(events) {
        var currentMipSkip = 0
        var sharedTextureUploads = 0
        var sharedTextureReleases = 0
        val boundHandles = mutableMapOf<String, ContentTextureHandle>()

        override val textureMipSkip: Int get() = currentMipSkip

        override fun uploadTexture(asset: TextureAsset): ContentTextureUpload {
            sharedTextureUploads++
            events += "upload shared-texture"
            return ContentTextureUpload(FakeHandle()) {
                sharedTextureReleases++
                events += "release shared-texture"
            }
        }

        override fun uploadGroup(
            pipeline: FakePipeline,
            feature: ContentFeature,
            includeGeometry: Boolean,
            sharedTextures: Map<Int, ContentTextureHandle>,
        ): ContentGroupUpload {
            sharedTextures[ARRAY_BINDING]?.let { boundHandles[feature.name] = it }
            return ContentGroupUpload(Block(), ContentUpload(null) { events += "release ${feature.name}" })
        }
    }

    private class Block : UniformBlock {
        override fun binding(frameIndex: Int): MaterialBinding = error("not recorded in these tests")
        override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) = Unit
    }

    private companion object {
        const val ARRAY_BINDING = 3
        const val KEY_PALETTE_A = "palette-a"
        val ARRAY_TEXTURE = TextureAsset(ByteArray(2 * 2 * 4 * 2), 2, 2, layerCount = 2)
        val SPEC = PipelineSpec(
            vertexFormat = VertexFormat.PositionColorUv,
            vertexShader = ShaderSource.ResourcePath("terrain.wgsl", "vertexMain"),
            fragmentShader = ShaderSource.ResourcePath("terrain.wgsl", "fragmentMain"),
            uniforms = LAYOUT,
            materialBindings = GroupBindings(
                listOf(
                    ResourceBinding(0, ResourceKind.UniformBuffer, setOf(ShaderStage.Vertex, ShaderStage.Fragment)),
                    ResourceBinding(ARRAY_BINDING, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment), arrayed = true),
                ),
            ),
        )
    }
}
