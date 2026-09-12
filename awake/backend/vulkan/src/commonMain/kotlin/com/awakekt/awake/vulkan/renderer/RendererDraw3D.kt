/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.passes.instancedUniformFloats
import com.awakekt.awake.render.passes.uniformFloats
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.pipeline.depthSortKey
import com.awakekt.awake.render.pipeline.instancedDrawKind
import com.awakekt.awake.render.pipeline.resolveInstanced
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.mesh.AlphaInstanceBuffer
import com.awakekt.awake.vulkan.mesh.FrameInstanceBuffer
import com.awakekt.awake.vulkan.mesh.InstanceBuffer
import com.awakekt.awake.vulkan.mesh.Mesh
import com.awakekt.awake.vulkan.mesh.SkinnedInstanceBuffer
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkOffset2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.VulkanMaterialBinding
import kotlin.collections.getOrNull
import kotlin.math.ceil
import kotlin.text.get
import com.awakekt.awake.render.material.Material as RenderMaterial
import com.awakekt.awake.vulkan.renderer.Renderer as VulkanRenderer

/** Generic Vulkan draw preparation and typed feature-facing frame helpers. Whole-frame packet
 * execution is owned by [RendererGpuPassExecutor]. */

/** Renders one frame: waits for this frame-in-flight slot, acquires a swapchain image,
 * prepares each source draw's MVP matrix (model combined with the packet view/projection)
 * into a concrete material uniform slot, records and submits a command buffer that draws
 * every call in order, then presents. Mutable per-frame resources (material uniforms,
 * debug-line uniforms/buffers, and UI dynamic meshes/descriptors) are indexed by the same
 * frame slot, so this path only waits that slot's fence rather than stalling the entire
 * device after every submit.
 *
 * Frame acquisition itself
 * is [acquireSwapchainImage] ([RendererSwapchainAcquire.kt]), split into its own file to
 * keep both detekt's method-length limit and this file's function-count limit. */
internal fun VulkanRenderer.prepareGpuDraws(
    frameIndex: Int,
    viewProjection: Mat4,
    cameraPosition: Vec3f,
    draws: List<GpuDrawRequest>,
    isTransparent: Boolean,
    materialUsage: MutableMap<RenderMaterial, Int> = mutableMapOf(),
    lightUniforms: FloatArray,
    shadowCascades: GpuShadowCascadeData? = null,
    fogColor: Color = Color.Black,
    fogDensity: Float = 0f,
): List<PreparedDrawCall> {
    var instancedIndex = 0
    return buildList(draws.size) {
        for (cmd in draws) {
            prepareGpuDraw(
                cmd = cmd,
                frameIndex = frameIndex,
                instancedIndex = instancedIndex,
                isTransparent = isTransparent,
                materialUsage = materialUsage,
                viewProjection = viewProjection,
                cameraPosition = cameraPosition,
                lightUniforms = lightUniforms,
                shadowCascades = shadowCascades,
                fogColor = fogColor,
                fogDensity = fogDensity,
            )?.let {
                add(it)
                if (cmd.instanceModels != null) instancedIndex++
            }
        }
    }
}

internal fun VulkanRenderer.prepareGpuDraw(
    cmd: GpuDrawRequest,
    frameIndex: Int,
    instancedIndex: Int,
    isTransparent: Boolean,
    materialUsage: MutableMap<RenderMaterial, Int> = mutableMapOf(),
    viewProjection: Mat4,
    cameraPosition: Vec3f,
    lightUniforms: FloatArray,
    shadowCascades: GpuShadowCascadeData? = null,
    fogColor: Color = Color.Black,
    fogDensity: Float = 0f,
): PreparedDrawCall? {
    val mesh = cmd.mesh as Mesh
    val material = cmd.material as Material
    val instanceModels = cmd.instanceModels
    if (instanceModels != null) {
        if (instanceModels.isEmpty()) return null
        val kind = cmd.instancedDrawKind() ?: return null
        val instancedPipeline = pipelines.resolveInstanced(mesh.format, kind) ?: return null
        val instanceBuffer = instanceBufferForRun(instancedIndex).also {
            it.update(frameIndex, instanceModels)
        }
        val uniformSlotIndex = materialUsage.nextSlot(material)
        val uniformFloats = cmd.instancedUniformFloats(
            kind = kind,
            viewProjection = viewProjection,
            lightPayload = lightUniforms,
            cameraEye = cameraPosition,
            shadowCascades = shadowCascades,
            fogColor = fogColor,
            fogDensity = fogDensity,
            materialUniformFloatCount = material.uniformFloatCount,
        )
        val binding = material.updateUniformBuffer(frameIndex, uniformSlotIndex, uniformFloats)
        val palettes = cmd.instanceJointPalettes?.let {
            skinnedInstanceBufferForRun(instancedIndex).also { buffer ->
                buffer.update(frameIndex, it)
            }
        }
        val colors = if (kind == InstancedDrawKind.Particle) {
            alphaInstanceBufferForRun(instancedIndex).also { it.update(frameIndex, cmd.instanceColors.orEmpty()) }
        } else {
            null
        }
        val frames = if (kind == InstancedDrawKind.Particle) {
            frameInstanceBufferForRun(instancedIndex).also { it.update(frameIndex, cmd.instanceFrames.orEmpty()) }
        } else {
            null
        }
        return PreparedDrawCall(
            mesh = mesh,
            isTransparent = isTransparent,
            pipeline = instancedPipeline,
            material = material,
            frameIndex = frameIndex,
            uniformSlotIndex = uniformSlotIndex,
            materialBinding = binding,
            instanceBuffer = instanceBuffer,
            instanceCount = instanceModels.size,
            jointPaletteBuffer = palettes,
            alphaInstanceBuffer = colors,
            frameInstanceBuffer = frames,
            depthSortKey = cmd.depthSortKey(cameraPosition),
        )
    }
    val pipeline = pipelineFor(mesh.format, cmd.cullMode, isTransparent) ?: return null
    val uniformSlotIndex = materialUsage.nextSlot(material)
    val uniformFloats = cmd.uniformFloats(
        materialUniformFloatCount = material.uniformFloatCount,
        viewProjection = viewProjection,
        cameraEye = cameraPosition,
        lightPayload = lightUniforms,
        shadowCascades = shadowCascades,
        fogColor = fogColor,
        fogDensity = fogDensity,
    )
    val binding = material.updateUniformBuffer(frameIndex, uniformSlotIndex, uniformFloats)
    return PreparedDrawCall(
        mesh = mesh,
        isTransparent = isTransparent,
        pipeline = pipeline,
        material = material,
        frameIndex = frameIndex,
        uniformSlotIndex = uniformSlotIndex,
        materialBinding = binding,
        depthSortKey = cmd.depthSortKey(cameraPosition),
    )
}

internal fun RenderViewport.toVkViewport(): VkViewport =
    VkViewport(x = x, y = y, width = width, height = height)

internal fun RenderViewport.toVkScissor(): VkRect2D = VkRect2D(
    offset = VkOffset2D(x.toInt(), y.toInt()),
    // Rounded up so a fractional edge does not scissor away the viewport's last column/row.
    extent = VkExtent2D(ceil(width).toInt(), ceil(height).toInt()),
)

/** Binds+draws each [drawCalls] entry through the shared per-draw recording loop, against
 * whatever pipeline the caller already bound -- [Renderer.renderToTexture]'s offscreen path,
 * which groups by pipeline and binds each group itself. The swapchain frame reaches the same
 * loop through `SharedOpaqueRenderFeature.recordCommands` instead. */
internal fun VulkanRenderer.recordDrawCalls(commandBuffer: Long, drawCalls: List<PreparedDrawCall>) {
    commandRecorder.commandBuffer = commandBuffer
    sharedOpaqueFeature.recordDraws(commandRecorder, drawCalls)
}

/** Waits until the current frame-in-flight slot is no longer referenced by the GPU before
 * CPU code rewrites host-visible resources assigned to that slot. This is intentionally much
 * narrower than `vkDeviceWaitIdle`: other submitted frame slots may continue running. */
internal fun VulkanRenderer.waitForCurrentFrameResourceSlot() {
    val currentFrame = swapchainManager.currentFrame
    val fence = swapchainManager.inFlightFences.getOrNull(currentFrame) ?: return
    if (fence == 0L) return
    Vulkan.vkWaitForFences(device, longArrayOf(fence), true, Long.MAX_VALUE)
}

/**
 * Also this backend's [PreparedDraw]: the port's members are computed off the fields already
 * here, so the shared opaque feature iterates these directly instead of a second per-draw object
 * being built for it every frame. Every getter resolves to an object created at
 * resource-creation time (a material's uniform slot, a mesh's buffer binding), so reading one
 * allocates nothing.
 */
internal data class PreparedDrawCall(
    val mesh: Mesh,
    val isTransparent: Boolean,
    override val pipeline: RenderPipeline,
    val material: Material,
    val frameIndex: Int,
    val uniformSlotIndex: Int,
    /** The descriptor set `prepareDrawCalls` just wrote this draw's uniforms into. */
    override val materialBinding: VulkanMaterialBinding,
    /** Non-null only for an instanced draw resolved to an instanced pipeline --
     * [recordDrawCalls] then binds it and issues one `drawInstanced` instead of `draw`. */
    val instanceBuffer: InstanceBuffer? = null,
    val instanceCount: Int = 0,
    /** Non-null only for an ANIMATED instanced draw, where it
     * accompanies [instanceBuffer] -- per-instance model matrices still come through that. */
    val jointPaletteBuffer: SkinnedInstanceBuffer? = null,
    /** Non-null only for a billboard-particle instanced draw,
     * bound at binding 2 alongside [instanceBuffer]'s binding 1. */
    val alphaInstanceBuffer: AlphaInstanceBuffer? = null,
    /** Non-null only for a billboard-particle instanced draw, bound
     * at binding 3 alongside [alphaInstanceBuffer]'s binding 2. */
    val frameInstanceBuffer: FrameInstanceBuffer? = null,
    /** Squared distance to the camera eye, computed once at preparation rather than inside the
     * sort comparator. Last in the list on purpose: one construction site below is positional. */
    override val depthSortKey: Float = 0f,
) : PreparedDraw {
    override val transparent: Boolean get() = isTransparent
    override val vertexFormat: VertexFormat get() = mesh.format

    /** Mesh identity -- clusters draws sharing a mesh so consecutive calls reuse its vertex and
     * index buffer bindings. */
    override val batchKey: Int get() = mesh.hashCode()

    override val vertexBuffer get() = mesh.vertexBinding
    override val indexBuffer get() = mesh.indexBinding
    override val elementCount get() = mesh.indexCount

    /** [instanceCount] is 0 for a non-instanced draw; the port's count is the real one Vulkan
     * issues, which is 1 there. */
    override val instances get() = if (instanceBuffer == null) 1 else instanceCount
    override val instanceVertexBuffer get() = instanceBuffer?.binding(frameIndex)
    override val jointPaletteBinding get() = jointPaletteBuffer?.binding(frameIndex)
    override val instanceColorBuffer get() = alphaInstanceBuffer?.binding(frameIndex)
    override val instanceFrameBuffer get() = frameInstanceBuffer?.binding(frameIndex)
    override val depthMaterialBinding get() = materialBinding
    override val depthJointPaletteBinding get() = jointPaletteBinding
}

private fun MutableMap<RenderMaterial, Int>.nextSlot(material: RenderMaterial): Int {
    val slot = this[material] ?: 0
    this[material] = slot + 1
    return slot
}
