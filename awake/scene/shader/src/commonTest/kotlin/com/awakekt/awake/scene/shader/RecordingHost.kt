/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.shader

import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter

/**
 * A content-feature host with no GPU: it builds each attached feature over a block that keeps what is
 * written to it, so a test can draw a frame and read the uniforms and draw calls an effect produced.
 */
internal class RecordingHost : ContentFeatureHost {
    val attachments = mutableListOf<Attachment>()

    /** When set, the next attach throws, as a backend that refuses a pipeline does. */
    var refuseNext = false

    override suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature {
        if (refuseNext) {
            refuseNext = false
            error("refused")
        }
        val feature = source.resolve(RenderBackend.Vulkan)
        val block = Block(requireNotNull(feature.spec.uniforms))
        val geometry = feature.geometry?.let { ContentGeometry(object : BufferHandle {}, object : BufferHandle {}, elementCount = 6) }
        val attachment = Attachment(feature.build(object : PipelineHandle {}, block, geometry), block)
        attachments += attachment
        return AttachedContentFeature { attachment.detached = true }
    }

    /** The attachments still drawing. */
    val live: List<Attachment> get() = attachments.filterNot { it.detached }
}

internal class Attachment(private val feature: RenderFeature<RenderFrameContext>, private val block: Block) {
    var detached = false

    /** Records one frame and returns what it wrote: empty when it drew nothing. */
    fun drawFrame(): Frame {
        val recorder = Recorder()
        block.written = FloatArray(0)
        feature.recordCommands(Context(recorder))
        return Frame(recorder.calls, block.written)
    }
}

/** One recorded frame: the draw calls, and the uniform block as written. */
internal class Frame(val calls: List<String>, val uniforms: FloatArray) {
    val drew: Boolean get() = calls.isNotEmpty()
    val time: Float get() = uniforms[CLOCK]
    val delta: Float get() = uniforms[CLOCK + 1]
    val modelTranslationX: Float get() = uniforms[MODEL + 12]

    /** The first component of the [index]th parameter. */
    fun parameter(index: Int): Float = uniforms[PARAMS + index * 4]

    private companion object {
        // The document's uniform block: viewProjection, inverseViewProjection, model, cameraPosition,
        // sunDirection, clock, viewport, params.
        const val MODEL = 32
        const val CLOCK = 56
        const val PARAMS = 64
    }
}

internal class Block(private val layout: UniformLayout) : UniformBlock {
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
        calls += "bindMaterial"
    }

    override fun bindVertexBuffer(binding: Int, buffer: BufferHandle) {
        calls += "bindVertexBuffer"
    }

    override fun bindIndexBuffer(buffer: BufferHandle) {
        calls += "bindIndexBuffer"
    }

    override fun draw(vertexCount: Int, instanceCount: Int) {
        calls += "draw"
    }

    override fun drawIndexed(indexCount: Int, instanceCount: Int) {
        calls += "drawIndexed"
    }
}

private class Context(override val recorder: CommandRecorder) : RenderFrameContext {
    override val frameIndex = 0
    override val groupedDrawCalls: Map<out PipelineHandle, List<PreparedDraw>> = emptyMap()
    override val primaryPipeline: PipelineHandle = object : PipelineHandle {}
    override val viewProjection: Mat4 = Mat4().apply { identity() }
    override val cameraEye: Vec3f = Vec3f(0f, 1f, 0f)
    override val surfaceWidth = 64
    override val surfaceHeight = 64
}
