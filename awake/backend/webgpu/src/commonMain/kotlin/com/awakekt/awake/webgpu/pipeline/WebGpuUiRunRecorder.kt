/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.core.graphics2d.TextureCompositeMode
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.passes2d.BackdropBlurRecording
import com.awakekt.awake.render.passes2d.TexturedPrimitiveRun
import com.awakekt.awake.render.passes2d.UiPipelineKind
import com.awakekt.awake.render.passes2d.UiRunRecorder
import com.awakekt.awake.render.renderer.RenderStatsCounter
import com.awakekt.awake.webgpu.material.Material
import com.awakekt.awake.webgpu.ui.BackdropBlurResources
import com.awakekt.awake.webgpu.ui.DynamicMesh
import com.awakekt.awake.webgpu.ui.UiRenderPipeline
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPURenderPassEncoder
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor

/**
 * WebGPU's half of [com.awakekt.awake.render.passes2d.SharedUiRenderFeature] -- the
 * mirror of Vulkan's `VulkanUiRunRecorder`. Built per UI pass, around the encoder whose render
 * pass the caller has already begun.
 *
 * [textureMeshForPrimitive] is a pool, not one shared mesh: `queue.writeBuffer` is queue-
 * scheduled rather than interleaved with encoding, so a run's later primitive writing a shared
 * mesh would land before an earlier, already-encoded draw reads it.
 *
 * With a [backdrop], a blur ends the pass and begins another, so every draw goes through the
 * backdrop's current pass rather than [encoder], and the caller ends whichever pass is current.
 */
@Suppress("LongParameterList")
internal class WebGpuUiRunRecorder(
    private val encoder: GPURenderPassEncoder,
    private val quad: UiRenderPipeline,
    private val roundedQuad: UiRenderPipeline?,
    private val glyph: UiRenderPipeline?,
    private val textures: Map<TextureCompositeMode, UiRenderPipeline>,
    private val textureMeshForPrimitive: (Int) -> DynamicMesh,
    private val stats: RenderStatsCounter,
    private val backdrop: WebGpuBackdropTarget? = null,
) : UiRunRecorder<DynamicMesh> {
    private val pass: GPURenderPassEncoder get() = backdrop?.pass ?: encoder
    private var outputLevel = 0

    override fun bindPipeline(kind: UiPipelineKind): Boolean {
        val pipeline = when (kind) {
            UiPipelineKind.Quad -> quad
            UiPipelineKind.RoundedQuad -> roundedQuad
            UiPipelineKind.Glyph -> glyph
            // A texture chooses its exact blend/alpha-convention pipeline in
            // drawTexturePrimitive. Requiring the straight-alpha default here would discard a
            // run that contains only premultiplied render-target textures.
            UiPipelineKind.Texture -> if (textures.isEmpty()) null else quad
            UiPipelineKind.BackdropComposite -> return bindBackdropComposite()
        }
        // Texture binds nothing up front: each primitive binds its own material bind group.
        if (pipeline != null && kind != UiPipelineKind.Texture) {
            pass.setPipeline(pipeline.pipeline)
            // Glyph's group 0 is its font's -- atlas and uniforms -- bound in bindGlyphFont.
            if (kind != UiPipelineKind.Glyph) pass.bindGroup(0u, pipeline.screenSizeBindGroup)
        }
        return pipeline != null
    }

    override fun bindGlyphFont(font: UiFont): Boolean {
        val pipeline = glyph ?: return false
        pass.bindGroup(0u, pipeline.fontBindGroup(font))
        return true
    }

    override fun drawMesh(mesh: DynamicMesh) {
        val pass = pass
        pass.setVertexBuffer(0u, mesh.vertexBufferRef())
        pass.setIndexBuffer(mesh.indexBufferRef(), DynamicMesh.indexFormat)
        pass.drawIndexed(mesh.drawIndexCount.toUInt())
        stats.recordDraw(mesh.drawIndexCount)
    }

    override fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        pass.setScissorRect(x.toUInt(), y.toUInt(), width.toUInt(), height.toUInt())
    }

    override fun drawTexturePrimitive(primitive: TexturedPrimitiveRun, slotIndex: Int) {
        val pipeline = textures[primitive.compositeMode] ?: return
        val material = primitive.texture as Material
        // Its owner destroyed it after this UI was staged, so it is no longer on screen to draw.
        if (!material.isSampleable) return
        val mesh = textureMeshForPrimitive(slotIndex)
        pass.setPipeline(pipeline.pipeline)
        pass.bindGroup(0u, pipeline.bindGroupFor(material))
        mesh.update(primitive.vertices, primitive.indices)
        drawMesh(mesh)
    }

    /**
     * Ends the UI pass, copies the region the blur reads into level 0, runs every pass of [blur]
     * in passes of their own, and begins the UI pass again with its colour loaded.
     */
    override fun blurBackdrop(blur: BackdropBlurRecording<DynamicMesh>, more: Boolean): Boolean {
        val target = backdrop ?: return false
        val work = blur.work
        val copyable = GPUTextureUsage.CopySrc in target.texture.usage
        if (!copyable || work.width != target.width || work.height != target.height || !target.resources.fits(work.width, work.height)) {
            return false
        }
        target.pass.end()
        target.resources.capture(target.commandEncoder, target.texture, work.region)
        for (index in work.passes.indices) {
            target.resources.recordPass(target.commandEncoder, work.passes[index], blur.passMeshes[index], stats)
        }
        target.pass = target.commandEncoder.beginRenderPass(
            RenderPassDescriptor(
                colorAttachments = listOf(
                    RenderPassColorAttachment(view = target.view, loadOp = GPULoadOp.Load, storeOp = GPUStoreOp.Store),
                ),
            ),
        )
        outputLevel = work.blur.outputLevel
        return true
    }

    private fun bindBackdropComposite(): Boolean {
        val target = backdrop ?: return false
        if (outputLevel == 0) return false
        pass.setPipeline(target.composite.pipeline)
        pass.bindGroup(0u, target.composite.levelBindGroup(target.resources.levelView(outputLevel), target.resources.sampler))
        return true
    }
}

/**
 * The target a WebGPU UI pass draws into, as a backdrop blur needs it: the frame's command
 * encoder to end the pass on and copy with, the texture it copies from, and the [pass] currently
 * open over [view], which a blur replaces.
 *
 * @property commandEncoder The encoder the UI pass was begun on.
 * @property texture The target texture; it must allow copying from for a blur to run.
 * @property view The view the UI pass draws into.
 * @property width The target's width.
 * @property height The target's height.
 * @property resources The levels, blur pipeline and sampler.
 * @property composite The glass pipeline.
 * @property pass The open UI pass; the caller ends this one, not the one it began.
 */
@Suppress("LongParameterList")
internal class WebGpuBackdropTarget(
    val commandEncoder: GPUCommandEncoder,
    val texture: GPUTexture,
    val view: GPUTextureView,
    val width: Int,
    val height: Int,
    val resources: BackdropBlurResources,
    val composite: UiRenderPipeline,
    var pass: GPURenderPassEncoder,
)
