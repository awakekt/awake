/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShaderDocumentContentFeatureTest {
    private val water = ShaderDocuments.compile(ShaderDocumentFixtures.WATER)
    private val sky = ShaderDocuments.compile(ShaderDocumentFixtures.SKY)
    private val pixel = TextureAsset(ByteArray(4), width = 1, height = 1)
    private val waterTextures = mapOf("ripples" to pixel, "foam" to pixel)

    // --- the feature an engine attaches

    @Test
    fun aFullScreenDocumentIsAVertexlessBackgroundPaintedFirst() {
        RenderBackend.entries.forEach { backend ->
            val feature = sky.contentFeature(sky.newInputs()).resolve(backend)
            assertEquals(VertexFormat.None, feature.spec.vertexFormat)
            assertEquals(PipelineVariant.Background, feature.spec.variant)
            assertEquals(ContentPaint.BeforeGeometry, feature.paint)
            assertNull(feature.geometry)
        }
    }

    @Test
    fun aBlendedPlaneDrawsItsGridAfterSceneGeometry() {
        RenderBackend.entries.forEach { backend ->
            val feature = water.contentFeature(water.newInputs(), waterTextures).resolve(backend)
            assertEquals(VertexFormat.PositionUv, feature.spec.vertexFormat)
            assertEquals(PipelineVariant.AlphaBlended, feature.spec.variant)
            assertEquals(ContentPaint.AfterGeometry, feature.paint)
            assertEquals(setOf(2, 3), feature.textures.keys)
            assertEquals(4 * 4 * 6, feature.geometry?.indices?.size, "4 x 4 quads, two triangles each")
        }
    }

    @Test
    fun anOverlayBlendsOverEverything() {
        val tint = ShaderDocuments.compile(ShaderDocumentFixtures.TINT)
        val feature = tint.contentFeature(tint.newInputs()).resolve(RenderBackend.Vulkan)
        assertEquals(PipelineVariant.Overlay, feature.spec.variant)
        assertEquals(ContentPaint.AfterGeometry, feature.paint)
    }

    /** An engine registers one pipeline per spec, so two effects of one document need specs that differ. */
    @Test
    fun eachResolveHasAUniformLayoutOfItsOwn() {
        val first = sky.contentFeature(sky.newInputs()).resolve(RenderBackend.Vulkan)
        val second = sky.contentFeature(sky.newInputs()).resolve(RenderBackend.Vulkan)
        assertNotEquals(first.spec, second.spec)
    }

    @Test
    fun aMissingAnUnknownOrANon2DTextureIsRejectedByName() {
        val missing = assertFailsWith<ShaderDocumentException> { water.contentFeature(water.newInputs(), mapOf("ripples" to pixel)) }
        assertTrue(missing.issues.any { "'foam'" in it.message }, missing.issues.toString())
        val extra = assertFailsWith<ShaderDocumentException> { water.contentFeature(water.newInputs(), waterTextures + ("sand" to pixel)) }
        assertTrue(extra.issues.any { "'sand'" in it.message }, extra.issues.toString())
        val array = TextureAsset(ByteArray(8), width = 1, height = 1, layerCount = 2)
        val layered = assertFailsWith<ShaderDocumentException> { water.contentFeature(water.newInputs(), waterTextures + ("foam" to array)) }
        assertTrue(layered.issues.any { "'foam'" in it.message && "2D" in it.message }, layered.issues.toString())
    }

    @Test
    fun inputsBelongToTheDocumentTheyWereMadeFor() {
        val tint = ShaderDocuments.compile(ShaderDocumentFixtures.TINT)
        assertFailsWith<IllegalArgumentException> { water.contentFeature(tint.newInputs(), waterTextures) }
    }

    // --- parameters

    @Test
    fun newInputsHoldTheDefaultsFourFloatsEach() {
        assertContentEquals(floatArrayOf(0.2f, 0f, 0f, 0f, 0f, 0.3f, 0.6f, 0.8f), water.newInputs().parameters)
    }

    @Test
    fun packedValuesReplaceDefaultsByName() {
        val inputs = water.newInputs()
        val issues = water.packParameters(mapOf("tint" to listOf(1f, 0.5f, 0.25f, 1f)), inputs)
        assertEquals(emptyList(), issues)
        assertContentEquals(floatArrayOf(0.2f, 0f, 0f, 0f, 1f, 0.5f, 0.25f, 1f), inputs.parameters)
    }

    @Test
    fun aBadValueIsReportedByNameAndKeepsItsDefault() {
        val inputs = water.newInputs()
        val issues = water.packParameters(
            mapOf("tint" to listOf(1f, 0f, 0f), "amplitude" to listOf(Float.NaN), "speed" to listOf(1f)),
            inputs,
        )
        assertEquals(setOf("parameters.tint", "parameters.amplitude", "parameters.speed"), issues.map { it.path }.toSet())
        assertTrue(issues.single { it.path == "parameters.tint" }.message.contains("takes 4"), issues.toString())
        assertContentEquals(floatArrayOf(0.2f, 0f, 0f, 0f, 0f, 0.3f, 0.6f, 0.8f), inputs.parameters)
    }

    // --- recording

    @Test
    fun aFullScreenEffectWritesItsUniformsInLayoutOrderThenDrawsOneTriangle() {
        val inputs = sky.newInputs().apply {
            timeSeconds = 2.5f
            deltaSeconds = 0.25f
        }
        val recorded = record(sky.contentFeature(inputs).resolve(RenderBackend.Vulkan), geometry = null)
        assertEquals(listOf("bindPipeline", "bindMaterial:Material", "draw:3"), recorded.recorder.calls)
        val floats = recorded.block.written
        val clock = 16 * 3 + 4 * 2
        assertEquals(listOf(2.5f, 0.25f, 0f, 0f), floats.copyOfRange(clock, clock + 4).toList(), "clock after three matrices, the camera and the sun")
        val viewport = clock + 4
        assertEquals(listOf(640f, 480f, 1f / 640f, 1f / 480f), floats.copyOfRange(viewport, viewport + 4).toList())
        val params = viewport + 4
        assertEquals(listOf(0.9f, 0.5f, 0.3f, 1f), floats.copyOfRange(params + 4, params + 8).toList(), "the second parameter, bottom")
    }

    @Test
    fun aPlaneBindsItsGeometryAndCopiesItsModelMatrix() {
        val inputs = water.newInputs()
        inputs.setModel(Mat4().apply { identity(); data[12] = 5f })
        val geometry = ContentGeometry(object : BufferHandle {}, object : BufferHandle {}, elementCount = 96)
        val recorded = record(water.contentFeature(inputs, waterTextures).resolve(RenderBackend.WebGpu), geometry)
        assertEquals(listOf("bindPipeline", "bindMaterial:Material", "bindVertexBuffer:0", "bindIndexBuffer", "drawIndexed:96"), recorded.recorder.calls)
        assertEquals(5f, recorded.block.written[16 * 2 + 12], "the model matrix's translation")
    }

    @Test
    fun anInvisibleEffectRecordsNothing() {
        val inputs = sky.newInputs().apply { visible = false }
        val recorded = record(sky.contentFeature(inputs).resolve(RenderBackend.Vulkan), geometry = null)
        assertEquals(emptyList(), recorded.recorder.calls)
    }

    private class Recorded(val recorder: Recorder, val block: Block)

    private fun record(feature: ContentFeature, geometry: ContentGeometry?): Recorded {
        val block = Block(requireNotNull(feature.spec.uniforms))
        val recorder = Recorder()
        val renderFeature = feature.build(object : PipelineHandle {}, block, geometry)
        renderFeature.recordCommands(Frame(recorder))
        return Recorded(recorder, block)
    }

    private class Block(private val layout: UniformLayout) : UniformBlock {
        var written = FloatArray(0)

        override fun binding(frameIndex: Int): MaterialBinding = object : MaterialBinding {}

        override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) {
            written = UniformWriter(layout).apply(fill).build()
        }
    }

    private class Recorder : CommandRecorder {
        val calls = mutableListOf<String>()

        override fun bindPipeline(pipeline: PipelineHandle) {
            calls += "bindPipeline"
        }

        override fun bindMaterial(semantic: BindingSemantic, binding: MaterialBinding) {
            calls += "bindMaterial:$semantic"
        }

        override fun bindVertexBuffer(binding: Int, buffer: BufferHandle) {
            calls += "bindVertexBuffer:$binding"
        }

        override fun bindIndexBuffer(buffer: BufferHandle) {
            calls += "bindIndexBuffer"
        }

        override fun draw(vertexCount: Int, instanceCount: Int) {
            calls += "draw:$vertexCount"
        }

        override fun drawIndexed(indexCount: Int, instanceCount: Int) {
            calls += "drawIndexed:$indexCount"
        }
    }

    private class Frame(override val recorder: CommandRecorder) : RenderFrameContext {
        override val frameIndex = 0
        override val groupedDrawCalls: Map<out PipelineHandle, List<PreparedDraw>> = emptyMap()
        override val primaryPipeline: PipelineHandle = object : PipelineHandle {}
        override val viewProjection: Mat4 = Mat4().apply { identity() }
        override val cameraEye: Vec3f = Vec3f(0f, 1f, 0f)
        override val surfaceWidth = 640
        override val surfaceHeight = 480
    }
}
