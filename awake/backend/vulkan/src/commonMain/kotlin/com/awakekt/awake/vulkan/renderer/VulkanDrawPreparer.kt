/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.passes.drawsEdges
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.pipeline.canInstance
import com.awakekt.awake.render.pipeline.depthRenderKey
import com.awakekt.awake.render.pipeline.edges
import com.awakekt.awake.render.pipeline.instancedDrawKind
import com.awakekt.awake.render.pipeline.resolveInstanced
import com.awakekt.awake.vulkan.pipeline.RenderPipeline

/** Prepares one generic draw through Vulkan's existing resource preparation code.
 *
 * Named [VulkanDrawPreparer] to mirror [WebGpuDrawPreparer] on the sibling backend --
 * "Gpu" was redundant because Vulkan is already a GPU backend, not a qualifier.
 */
internal class VulkanDrawPreparer(
    private val renderer: Renderer,
    private val offscreen: Boolean = false,
) : GpuDrawPreparer {
    /** Each draw's uniform slot in its material and each instanced draw's instance buffers, by what
     * the draw is: a draw culled earlier in the list leaves every later one where it was. Each
     * optional stream (skinned palettes, per-instance colours, particle frames) owns its own dense
     * slot table so draws without that stream never create unused gap entries in sibling pools. */
    private val materialSlots = StableMaterialSlots()
    private val denseInstanceSlots = DenseInstanceSlots()

    /** Instanced casters' shared depth sets, for this batch. */
    private val sharedDepth = SharedDepthBindings()

    /** Whether this batch's wireframe-overlay edges have their frame block written yet. */
    private var edgeFrameWritten = false

    /** Each draw is prepared into this and copied out into its [GpuResolvedDraw] before the next. */
    private val scratch = PreparedDrawCall()

    /** The packet each draw resolved to last, handed back while it would resolve to the same one. */
    internal val resolvedDraws = ResolvedDrawCache()

    override fun canInstance(format: VertexFormat): Boolean = format in renderer.instancedPipelinesByFormat

    override fun canInstance(format: VertexFormat, cullMode: CullMode): Boolean = renderer.pipelines.canInstance(format, cullMode)

    override fun canTintInstances(format: VertexFormat, cullMode: CullMode): Boolean =
        renderer.pipelines.resolveInstanced(format, InstancedDrawKind.Plain, cullMode = cullMode)?.readsInstanceTint == true

    /** The shadow depth pipelines enable `depthClampEnable` exactly when the device has the feature. */
    override val clampsShadowDepth: Boolean get() = renderer.graphicsDevice.depthClamp

    /**
     * A prepared frame's draws bind the slot they were prepared in, frame block included, so drawn
     * again they read what they read; the batch prepared after a reused frame waits for every slot
     * first ([ReusedFrameGuard]). Only when the renderer opts in ([Renderer.redrawsStillFrames]).
     */
    override val redrawsPreparedFrames: Boolean get() = renderer.redrawsStillFrames

    override fun prepare(
        request: GpuDrawRequest,
        sourceIndex: Int,
        context: GpuDrawPreparationContext,
    ): GpuResolvedDraw? {
        if (sourceIndex == 0) startBatch(context)
        val edgePipeline = if (context.edges) edgePipelineFor(request) ?: return null else null
        if (edgePipeline != null) writeEdgeFrame(context)
        val frameIndex = frameIndex()
        val instancedKind = request.instancedDrawKind()
        val prepared = renderer.prepareGpuDraw(
            cmd = request,
            frameIndex = frameIndex,
            instancedIndex = denseInstanceSlots.modelSlotFor(request, instancedKind, sourceIndex),
            skinnedInstancedIndex = denseInstanceSlots.skinnedSlotFor(request, instancedKind),
            alphaInstancedIndex = denseInstanceSlots.alphaSlotFor(request, instancedKind),
            frameInstancedIndex = denseInstanceSlots.frameSlotFor(request, instancedKind),
            isTransparent = request.transparent,
            viewProjection = context.viewProjection,
            cameraPosition = context.cameraEye,
            cameraForward = context.cameraForward,
            lightUniforms = context.passUniforms,
            shadowCascades = context.cascades(),
            fogColor = context.environment.fogColor,
            fogDensity = context.environment.fogDensity,
            debugView = context.environment.debugView,
            exposure = context.environment.exposure,
            edgePipeline = edgePipeline,
            explicitUniformSlotIndex = materialSlots.slotFor(
                material = request.material,
                mesh = request.mesh,
                instanced = request.instanceModels != null,
                edges = edgePipeline != null,
            ),
            into = scratch,
        ) ?: return null
        return resolvePreparedDraw(request, frameIndex, sourceIndex, edgePipeline != null, prepared)
    }

    private fun resolvePreparedDraw(
        request: GpuDrawRequest,
        frameIndex: Int,
        sourceIndex: Int,
        isEdge: Boolean,
        prepared: PreparedDrawCall,
    ): GpuResolvedDraw {
        // An edge draws over the frame and casts nothing, so it has no depth pass to join.
        val kind = if (isEdge) ResolvedDrawCache.EDGE else ResolvedDrawCache.SINGLE
        if (isEdge) return resolvedDraws.resolve(kind, frameIndex, sourceIndex, prepared)
        val depthFeature = renderer.depthPrePassFeature ?: renderer.sceneDepthPassFeature
        val depthKey = request.depthRenderKey()
        val depthPipeline = depthFeature?.pipelineFor(depthKey, prepared.vertexFormat)
        val sourceMaterial = request.material as? com.awakekt.awake.vulkan.material.Material
        val missingMaskTexture = request.alphaMode == com.awakekt.awake.render.pipeline.AlphaMode.Masked &&
            sourceMaterial?.hasTexture != true
        return if (depthPipeline == null || sourceMaterial == null || missingMaskTexture) {
            resolvedDraws.resolve(kind, frameIndex, sourceIndex, prepared, depthKey)
        } else {
            resolvedDraws.resolve(
                kind,
                frameIndex,
                sourceIndex,
                prepared,
                depthRenderKey = depthKey,
                alphaCutoff = request.alphaCutoff,
                depthPipeline = depthPipeline,
                depthMaterialBinding = sharedDepth.bindingFor(request, depthKey, depthPipeline, prepared.pipeline, wholeBlock = false) {
                    prepared.materialBinding
                },
                depthJointPaletteBinding = prepared.jointPaletteBinding,
            )
        }
    }

    private fun frameIndex(): Int =
        if (offscreen) renderer.commandBuffers.size else renderer.swapchainManager.currentFrame

    /** The batch's edges are a pass of their own: their view-projection and debug code, written once. */
    private fun writeEdgeFrame(context: GpuDrawPreparationContext) {
        if (edgeFrameWritten) return
        renderer.graphicsDevice.frameUniforms.write(frameIndex(), context, context.cascades(), edges = true)
        edgeFrameWritten = true
    }

    /** Clears cached resolved packets and dense instance slot indices after `Renderer.trimMemory()`. */
    internal fun resetAfterTrim() {
        resolvedDraws.clear()
        denseInstanceSlots.clear()
        sharedDepth.clear()
    }

    /** The frame boundary: resets this batch's allocations and makes the frame slot safe to write. */
    private fun startBatch(context: GpuDrawPreparationContext) {
        materialSlots.begin()
        denseInstanceSlots.begin()
        sharedDepth.clear()
        edgeFrameWritten = false
        val frameIndex = frameIndex()
        if (offscreen) {
            // Only wait for a previous offscreen capture sharing this slot, never blocking on or
            // stalling the presented swapchain frames (#1687, #1709).
            renderer.awaitSubmittedOffscreenCommands()
        } else {
            // The GPU may still be reading this frame slot's uniforms from two frames ago. The UI
            // phase usually waited for it already, but a frame with no UI never did, and preparing
            // runs before acquiring the image, which is the next wait. Free when already waited.
            renderer.waitForCurrentFrameResourceSlot()
            // A frame handed back unprepared still reads the slot it was prepared in, which may be this one.
            if (renderer.bufferPools.reusedFrames.preparing()) renderer.waitForAllFrameResourceSlots()
            // Legacy offscreen work prepared against currentFrame may still read this slot.
            renderer.awaitSubmittedOffscreenCommandsFor(frameIndex)
        }
        // After waits: the block every lit draw of this batch reads beside its own.
        renderer.graphicsDevice.frameUniforms.write(frameIndex, context, context.cascades())
    }

    private fun GpuDrawPreparationContext.cascades(): GpuShadowCascadeData? = shadowCascadeData ?: shadowViewProjections
        .takeIf { it.isNotEmpty() }
        ?.let { GpuShadowCascadeData(it, FloatArray(it.size) { Float.MAX_VALUE }) }

    /** The edge pipeline that draws [request]'s edges, or null when a wireframe overlay draws none. */
    private fun edgePipelineFor(request: GpuDrawRequest): RenderPipeline? =
        (request.material as? com.awakekt.awake.vulkan.material.Material)
            ?.takeIf { request.drawsEdges(it.uniformFloatCount) }
            ?.let { renderer.pipelines.edges(request.mesh.format) }
}

private class DenseInstanceSlots {
    private val models = StableDrawSlots()
    private val skinned = StableDrawSlots()
    private val alpha = StableDrawSlots()
    private val frames = StableDrawSlots()

    fun begin() {
        models.begin()
        skinned.begin()
        alpha.begin()
        frames.begin()
    }

    fun clear() {
        models.clear()
        skinned.clear()
        alpha.clear()
        frames.clear()
    }

    fun modelSlotFor(request: GpuDrawRequest, kind: InstancedDrawKind?, fallback: Int): Int =
        if (kind != null) models.slotFor(request.mesh, request.material) else fallback

    fun skinnedSlotFor(request: GpuDrawRequest, kind: InstancedDrawKind?): Int =
        if (kind == InstancedDrawKind.Skinned) skinned.slotFor(request.mesh, request.material) else -1

    fun alphaSlotFor(request: GpuDrawRequest, kind: InstancedDrawKind?): Int {
        val needsAlpha = kind != null && kind != InstancedDrawKind.Skinned && !request.instanceColors.isNullOrEmpty()
        return if (needsAlpha) alpha.slotFor(request.mesh, request.material) else -1
    }

    fun frameSlotFor(request: GpuDrawRequest, kind: InstancedDrawKind?): Int {
        val needsFrames = kind == InstancedDrawKind.Particle && !request.instanceFrames.isNullOrEmpty()
        return if (needsFrames) frames.slotFor(request.mesh, request.material) else -1
    }
}
