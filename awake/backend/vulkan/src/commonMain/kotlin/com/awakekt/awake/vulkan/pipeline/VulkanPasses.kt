/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.passes.LinePass
import com.awakekt.awake.render.passes2d.BackdropBlurRecording
import com.awakekt.awake.render.passes2d.TexturedPrimitiveRun
import com.awakekt.awake.render.passes2d.UiPass
import com.awakekt.awake.render.passes2d.UiPipelineKind
import com.awakekt.awake.render.passes2d.UiRun
import com.awakekt.awake.render.passes2d.UiRunRecorder
import com.awakekt.awake.render.renderer.RenderStatsCounter
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.debug.LineMesh
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.enums.VkSubpassContents
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkOffset2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkRenderPassBeginInfo
import com.awakekt.awake.vulkan.ui.BackdropBlurResources
import com.awakekt.awake.vulkan.ui.DynamicMesh
import com.awakekt.awake.vulkan.ui.UiRenderPipeline

/**
 * Vulkan's implementations of the three scene-pass ports. Each is a plain translation into this
 * backend's own types -- the features themselves live once in `render:passes`.
 *
 * `frameIndex` is real here, unlike on WebGPU: every GPU resource below is per-frame-in-flight.
 */

/** Vulkan's [LinePass]. [lineMesh] is NOT destroyed here -- it stays a `Renderer`-owned
 * per-frame staging buffer. */
internal class VulkanLinePass(
    private val pipeline: LineRenderPipeline,
) : LinePass<VulkanRenderFrameContext> {
    override fun writeMvp(frameIndex: Int, mvp: FloatArray) = pipeline.writeMvp(frameIndex, mvp)

    override fun lineDraw(context: VulkanRenderFrameContext): PreparedDraw =
        LineDraw(pipeline, context.lineMesh, context.frameIndex)

    override fun destroy() = pipeline.destroy()
}

/** Vulkan's [UiPass]. */
internal class VulkanUiPass : UiPass<DynamicMesh, VulkanRenderFrameContext> {
    override fun runs(context: VulkanRenderFrameContext): List<UiRun<DynamicMesh>> = context.uiRuns

    override fun recorder(context: VulkanRenderFrameContext): UiRunRecorder<DynamicMesh>? {
        val pipelines = context.uiPipelines() ?: return null
        return VulkanUiRunRecorder(
            commandBuffer = context.commandBuffer,
            frameIndex = context.frameIndex,
            stats = context.stats,
            pipelines = pipelines,
            textureMeshForPrimitive = context::textureMeshForPrimitive,
        )
    }
}

/** This frame's staged debug lines as one non-indexed [PreparedDraw]. One small object per frame,
 * not per draw call -- every handle it exposes was created with its resource. */
private class LineDraw(
    private val linePipeline: LineRenderPipeline,
    private val lineMesh: LineMesh,
    private val frameIndex: Int,
) : PreparedDraw {
    override val pipeline: PipelineHandle get() = linePipeline
    override val materialBinding: MaterialBinding get() = linePipeline.uniformBinding(frameIndex)
    override val vertexBuffer: BufferHandle get() = lineMesh.binding(frameIndex)

    /** No index buffer at all: consecutive vertex pairs are the line segments under
     * `LINE_LIST`, so [elementCount] is a vertex count. */
    override val indexBuffer: BufferHandle? get() = null
    override val elementCount: Int get() = lineMesh.vertexCount(frameIndex)
}

/**
 * One frame's Vulkan translation of [UiRunRecorder]; cheap enough to build per UI pass. The swapchain
 * UI pass and the offscreen `renderUiToTexture` each build one over their own [pipelines], so both
 * record through the shared feature.
 */
internal class VulkanUiRunRecorder(
    private val commandBuffer: Long,
    private val frameIndex: Int,
    private val stats: RenderStatsCounter,
    private val pipelines: UiPipelineSet,
    private val textureMeshForPrimitive: (Int) -> DynamicMesh,
) : UiRunRecorder<DynamicMesh> {
    private var outputLevel = 0
    private var blurPassSlot = 0
    private var compositeSlot = 0

    override fun bindPipeline(kind: UiPipelineKind): Boolean {
        val pipeline = when (kind) {
            UiPipelineKind.Quad -> pipelines.quad
            UiPipelineKind.RoundedQuad -> pipelines.roundedQuad
            UiPipelineKind.Glyph -> pipelines.glyph
            // A texture binds its exact blend/alpha-convention pipeline in drawTexturePrimitive.
            // Requiring the straight-alpha default here dropped a run holding only premultiplied
            // render-target textures.
            UiPipelineKind.Texture -> pipelines.textures.values.firstOrNull()
            UiPipelineKind.BackdropComposite -> return bindBackdropComposite()
        }
        // Texture binds nothing up front: bindMaterial rebinds the pipeline per primitive,
        // since each one also needs its own descriptor set bound at the same point.
        // Glyph binds only the pipeline here: its group 0 is the font's, bound in bindGlyphFont.
        if (pipeline != null && kind != UiPipelineKind.Texture) pipeline.bind(commandBuffer)
        return pipeline != null
    }

    override fun bindGlyphFont(font: UiFont): Boolean {
        val pipeline = pipelines.glyph
        val atlas = pipelines.fontAtlases[font]
        if (pipeline == null || atlas == null) return false
        pipeline.bindFontAtlas(commandBuffer, font, atlas)
        return true
    }

    override fun drawMesh(mesh: DynamicMesh) {
        mesh.bind(frameIndex, commandBuffer)
        mesh.draw(frameIndex, commandBuffer, stats)
    }

    override fun setScissor(x: Int, y: Int, width: Int, height: Int) =
        Vulkan.vkCmdSetScissor(
            commandBuffer,
            0,
            arrayOf(VkRect2D(offset = VkOffset2D(x = x, y = y), extent = VkExtent2D(width = width, height = height))),
        )

    override fun drawTexturePrimitive(primitive: TexturedPrimitiveRun, slotIndex: Int) {
        val pipeline = pipelines.textures[primitive.compositeMode] ?: return
        val material = primitive.texture as Material
        val mesh = textureMeshForPrimitive(slotIndex)
        pipeline.bindMaterial(
            commandBuffer,
            frameIndex,
            slotIndex,
            material.samplerHandle,
            material.imageViewHandle,
        )
        mesh.update(frameIndex, primitive.vertices, primitive.indices)
        mesh.bind(frameIndex, commandBuffer)
        mesh.draw(frameIndex, commandBuffer, stats)
    }

    /**
     * Ends the UI pass, samples its target through every pass of [blur] into the levels, and
     * begins the pass again over the same framebuffer with its colour loaded: the continuing render
     * pass while [more] blurs follow, the target's own final pass after the last one.
     */
    override fun blurBackdrop(blur: BackdropBlurRecording<DynamicMesh>, more: Boolean): Boolean {
        val target = pipelines.backdrop ?: return false
        val work = blur.work
        if (work.width != target.width || work.height != target.height || !target.resources.fits(work.width, work.height)) return false
        Vulkan.vkCmdEndRenderPass(commandBuffer)
        target.toShaderRead(commandBuffer)
        for (index in work.passes.indices) {
            val pass = work.passes[index]
            target.resources.recordPass(
                commandBuffer,
                frameIndex,
                blurPassSlot++,
                pass,
                target.resources.sourceView(pass.sourceLevel, target.view),
                blur.passMeshes[index],
                stats,
            )
        }
        target.toAttachment(commandBuffer)
        val extent = VkExtent2D(target.width, target.height)
        Vulkan.vkCmdBeginRenderPass(
            commandBuffer,
            VkRenderPassBeginInfo(
                renderPass = if (more) target.continuePass else target.finalPass,
                framebuffer = target.framebuffer,
                renderArea = VkRect2D(extent = extent),
            ),
            VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
        )
        Vulkan.vkCmdSetViewport(commandBuffer, 0, arrayOf(VkViewport(width = target.width.toFloat(), height = target.height.toFloat())))
        outputLevel = work.blur.outputLevel
        return true
    }

    private fun bindBackdropComposite(): Boolean {
        val target = pipelines.backdrop ?: return false
        if (outputLevel == 0) return false
        target.composite.bindMaterial(
            commandBuffer,
            frameIndex,
            compositeSlot++,
            target.resources.sampler,
            target.resources.levelView(outputLevel),
        )
        return true
    }
}

/**
 * The target a UI pass draws into, as a backdrop blur needs it: to end the pass, sample [view],
 * and begin it again over [framebuffer] with what was drawn kept.
 *
 * @property resources The levels, blur pipeline and sampler.
 * @property composite The glass pipeline, built for this target's render pass.
 * @property view The target's colour view, which the first downsample reads.
 * @property width The target's width.
 * @property height The target's height.
 * @property framebuffer The framebuffer the pass draws through.
 * @property continuePass Resumes the pass when another blur follows.
 * @property finalPass Resumes the pass after the frame's last blur, ending in the layout the
 * target's own pass leaves it in.
 * @property toShaderRead Moves the target from attachment to sampled layout, after the pass ends.
 * @property toAttachment Moves it back before the pass resumes.
 */
@Suppress("LongParameterList")
internal class VulkanBackdropTarget(
    val resources: BackdropBlurResources,
    val composite: UiRenderPipeline,
    val view: Long,
    val width: Int,
    val height: Int,
    val framebuffer: Long,
    val continuePass: Long,
    val finalPass: Long,
    val toShaderRead: (Long) -> Unit,
    val toAttachment: (Long) -> Unit,
)
