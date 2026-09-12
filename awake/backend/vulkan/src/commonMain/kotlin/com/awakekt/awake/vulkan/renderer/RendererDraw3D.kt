/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.squaredDistanceFrom
import com.awakekt.awake.core.math.times
import com.awakekt.awake.render.command.GpuDrawCommand
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.renderer.CullMode
import com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.passes.uniforms.SceneFrameUniforms
import com.awakekt.awake.render.passes.uniforms.fogUniformFloats
import com.awakekt.awake.render.passes.uniforms.litShadowUniforms
import com.awakekt.awake.render.passes.uniforms.sceneLightUniforms
import com.awakekt.awake.render.passes.uniforms.texturedUniforms
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.pipeline.instancedDrawKind
import com.awakekt.awake.render.pipeline.resolveInstanced
import com.awakekt.awake.render.renderer.EnvironmentUniforms
import com.awakekt.awake.render.renderer.InstancedUniformLayout
import com.awakekt.awake.render.renderer.ParticleExtraUniformLayout
import com.awakekt.awake.render.renderer.ParticleUniformLayout
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.render.renderer.ShadowCascadeUniforms
import com.awakekt.awake.render.renderer.UNSHADOWED_CASCADES
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.render.renderer.directionalShadowTexelDepthScale
import com.awakekt.awake.render.renderer.shadowCascades

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
import kotlin.math.ceil
import com.awakekt.awake.render.material.Material as RenderMaterial

internal typealias Lens = com.awakekt.awake.core.math.Lens
internal typealias DrawCall = com.awakekt.awake.render.renderer.DrawCall
internal typealias SceneLight = com.awakekt.awake.render.renderer.SceneLight

/** The 3D frame path -- [Renderer.draw]'s whole-frame orchestration (wait/acquire -> update
 * uniforms -> record -> submit -> present), the shared per-draw-call recording loop, the
 * swapchain command-buffer recording (3D pass + UI overlay pass), the offscreen one-time-
 * command runner both [Renderer.renderToTexture]/[Renderer.readPixels] use, and debug-line
 * staging. See [Renderer]'s class doc comment for why this lives here as `internal` extension
 * functions rather than as members. */

/** Renders one frame: waits for this frame-in-flight slot, acquires a swapchain image,
 * prepares each [DrawCall]'s MVP matrix (model combined with [camera]'s view/projection)
 * into a concrete material uniform slot, records and submits a command buffer that draws
 * every call in order, then presents. Mutable per-frame resources (material uniforms,
 * debug-line uniforms/buffers, and UI dynamic meshes/descriptors) are indexed by the same
 * frame slot, so this path only waits that slot's fence rather than stalling the entire
 * device after every submit.
 *
 * Named `performDraw`, not `draw` -- [Renderer]'s `override fun draw(...)` (the actual
 * `RenderRenderer` interface method) is a one-line delegate to this extension function; an
 * extension function can't share its name with a member function it's called from without
 * the member call winning resolution and recursing into itself. Frame acquisition itself
 * is [acquireSwapchainImage] ([RendererSwapchainAcquire.kt]), split into its own file to
 * keep both detekt's method-length limit and this file's function-count limit. */
private val DEFAULT_LIGHT_FLOATS = floatArrayOf(
    0.4f, 0.8f, 0.4f, 0f,
    1.0f, 1.0f, 1.0f, 1.0f,
)

internal fun Renderer.prepareGpuDraws(
    frameIndex: Int,
    viewProjection: Mat4,
    cameraPosition: Vec3f,
    draws: List<GpuDrawCommand>,
    isTransparent: Boolean,
    materialUsage: MutableMap<RenderMaterial, Int> = mutableMapOf(),
): List<PreparedDrawCall> {
    val prepared = ArrayList<PreparedDrawCall>(draws.size)
    for (cmd in draws) {
        val mesh = cmd.mesh as Mesh
        val material = cmd.material as Material
        val pipeline = pipelineFor(mesh.format, CullMode.Back, isTransparent) ?: continue
        val uniformSlotIndex = materialUsage.nextSlot(material)
        val mvp = cmd.transform * viewProjection
        val uniformFloats = mvp.data + DEFAULT_LIGHT_FLOATS
        val binding = material.updateUniformBuffer(frameIndex, uniformSlotIndex, uniformFloats)
        prepared += PreparedDrawCall(
            mesh = mesh,
            isTransparent = isTransparent,
            pipeline = pipeline,
            material = material,
            frameIndex = frameIndex,
            uniformSlotIndex = uniformSlotIndex,
            materialBinding = binding,
            depthSortKey = cmd.transform.squaredDistanceFrom(cameraPosition),
        )
    }
    return prepared
}

internal fun Renderer.performDraw(input: GpuPassInput) {
    val currentFrame = swapchainManager.currentFrame
    val imageIndex = acquireSwapchainImage(currentFrame) ?: return

    val materialUsage = mutableMapOf<RenderMaterial, Int>()
    val preparedOpaque = prepareGpuDraws(
        currentFrame,
        input.viewProjection,
        input.cameraEye,
        input.opaqueDraws,
        isTransparent = false,
        materialUsage,
    )
    val preparedTransparent = prepareGpuDraws(
        currentFrame,
        input.viewProjection,
        input.cameraEye,
        input.transparentDraws,
        isTransparent = true,
        materialUsage,
    )
    val preparedDrawCalls = preparedOpaque + preparedTransparent

    Vulkan.vkResetCommandBuffer(commandBuffers[currentFrame], 0)
    recordCommandBuffer(
        commandBuffers[currentFrame],
        currentFrame,
        imageIndex,
        preparedDrawCalls,
        input.viewProjection,
        input.cameraEye,
    )
    submitAndPresent(currentFrame, imageIndex)
}

internal fun Renderer.performDraw(
    camera: Lens,
    drawCalls: List<DrawCall>,
    light: SceneLight,
    environment: EnvironmentUniforms = EnvironmentUniforms(
        showSky = showEnvironment,
        horizonColor = horizonColor,
        zenithColor = zenithColor,
        fogDensity = fogDensity,
        fogColor = fogColor,
        shadowsEnabled = shadowsEnabled,
    ),
) {
    val currentFrame = swapchainManager.currentFrame
    val imageIndex = acquireSwapchainImage(currentFrame) ?: return

    val aspect = resolvedSceneViewport()?.aspect
        ?: (swapchainManager.extent.width.toFloat() / swapchainManager.extent.height.toFloat())
    val viewProjection = camera.viewProjectionMatrix(aspect, clipSpace)
    val cascades = if (depthTarget != null && environment.shadowsEnabled) light.shadowCascades() else null
    val materialUsage = mutableMapOf<RenderMaterial, Int>()
    val preparedDrawCalls =
        prepareDrawCalls(
            currentFrame,
            viewProjection,
            drawCalls,
            light,
            cascades,
            materialUsage,
            cameraPosition = camera.eye,
            environment = environment,
        )

    Vulkan.vkResetCommandBuffer(commandBuffers[currentFrame], 0)
    recordCommandBuffer(
        commandBuffers[currentFrame],
        currentFrame,
        imageIndex,
        preparedDrawCalls,
        viewProjection,
        camera.eye,
        cascades,
    )

    submitAndPresent(currentFrame, imageIndex)
}

/** [Renderer.sceneViewport] trimmed to this frame's swapchain extent -- see
 * [RenderViewport.clampedTo] for why a caller's rect can outlive the surface it was measured
 * against. */
internal fun Renderer.resolvedSceneViewport(): RenderViewport? = sceneViewport?.clampedTo(
    swapchainManager.extent.width.toFloat(),
    swapchainManager.extent.height.toFloat(),
)

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
internal fun Renderer.recordDrawCalls(commandBuffer: Long, drawCalls: List<PreparedDrawCall>) {
    commandRecorder.commandBuffer = commandBuffer
    sharedOpaqueFeature.recordDraws(commandRecorder, drawCalls)
}

/** Waits until the current frame-in-flight slot is no longer referenced by the GPU before
 * CPU code rewrites host-visible resources assigned to that slot. This is intentionally much
 * narrower than `vkDeviceWaitIdle`: other submitted frame slots may continue running. */
internal fun Renderer.waitForCurrentFrameResourceSlot() {
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
}

/** Resolves each [drawCalls] entry against [Renderer.pipelinesByFormat] by its
 * [DrawCall.mesh]'s own [com.awakekt.awake.render.mesh.Mesh.format] and writes
 * its uniform buffer -- one shared pass for every vertex format a [Renderer] can draw,
 * replacing what used to be a separate `prepare*DrawCalls` function (and a separate
 * `Prepared*DrawCall` type) per format. A [drawCall] whose format has no entry in
 * [Renderer.pipelinesByFormat] is SKIPPED, not drawn through [Renderer.renderPipeline] as a
 * fallback -- rendering one format's vertex data through a different format's pipeline would
 * silently misinterpret the vertex buffer (wrong attribute count/offsets), which is worse
 * than not drawing it.
 *
 * [light] reaches [drawCall]s resolved to [Renderer.renderPipeline] itself (the primary/lit
 * format) and to the `PositionNormalColorUv` (textured/PBR) format; every other resolved
 * pipeline gets [DrawCall.extraUniformFloats] instead (e.g. a skinned mesh's joint palette),
 * exactly matching what each format's own shader expects (`triangle.wgsl`/`textured.wgsl`
 * read light, `skinned.wgsl` doesn't).
 *
 * [cascades] is `null` for every [Renderer] not built with shadow support (see
 * [Renderer.depthTarget]'s doc comment) -- in that case the written uniform buffer is byte-for-
 * byte identical to before shadows existed (mvp + 8 light floats). Non-null only appends 16
 * more floats (the frame's cascade matrices, same "Kotlin order gives the
 * conventional product" convention as `mvp` itself) for [Renderer.renderPipeline]-resolved
 * calls, matching `lit_shadow.wgsl`'s `Uniforms.lightMvp`. */
internal fun Renderer.prepareDrawCalls(
    frameIndex: Int,
    viewProjection: Mat4,
    drawCalls: List<DrawCall>,
    light: SceneLight,
    cascades: ShadowCascadeUniforms? = null,
    materialUsage: MutableMap<RenderMaterial, Int> = mutableMapOf(),
    cameraPosition: Vec3f = Vec3f.ZERO,
    environment: EnvironmentUniforms = EnvironmentUniforms(
        showSky = showEnvironment,
        horizonColor = horizonColor,
        zenithColor = zenithColor,
        fogDensity = fogDensity,
        fogColor = fogColor,
        shadowsEnabled = shadowsEnabled,
    ),
): List<PreparedDrawCall> {
    val prepared = ArrayList<PreparedDrawCall>(drawCalls.size)
    val lightUniforms = sceneLightUniforms(light, cameraPosition, shadowTexelDepthScale())
    val frame = SceneFrameUniforms(lightUniforms, cameraPosition, fogFloats(environment))
    var drawIndex = 0
    var instancedIndex = 0
    while (drawIndex < drawCalls.size) {
        val drawCall = drawCalls[drawIndex]
        val instanceModels = drawCall.instanceModels
        if (instanceModels != null) {
            // A separate path entirely: one uniform write and one GPU call for every transform,
            // instead of this loop's per-draw-call MVP. Null (no instanced pipeline for this
            // format, or nothing to draw) skips the call, same as an unresolved format below.
            // Particles carry their own camera-right/camera-up basis (particle.wgsl's
            // Uniforms) instead of the scene light -- every other instanced format keeps the
            // viewProjection+light block (instanced.wgsl/skinned_instanced.wgsl's Uniforms).
            val isParticle = drawCall.mesh.format == VertexFormat.PositionUv
            val instancedUniformFloats = if (isParticle) {
                UniformWriter(ParticleUniformLayout)
                    .put(viewProjection.data, UniformFields.Mvp)
                    .put(drawCall.extraUniformFloats, *ParticleExtraUniformLayout.fields)
                    .build()
            } else {
                UniformWriter(InstancedUniformLayout)
                    .put(viewProjection.data, UniformFields.Mvp)
                    .let(lightUniforms::writeDirectionalTo)
                    .build()
            }
            val instanced = prepareInstancedDrawCall(
                drawCall,
                frameIndex,
                instancedIndex,
                instancedUniformFloats,
                materialUsage,
            )
            if (instanced != null) {
                prepared += instanced
                instancedIndex += 1
            } else if (debugMode) {
                val animated = drawCall.instanceJointPalettes != null
                println(
                    "Awake (Vulkan): instanced DrawCall skipped -- no ${if (animated) "skinned-" else ""}" +
                        "instanced pipeline registered for mesh format ${drawCall.mesh.format}, " +
                        "or instanceModels was empty.",
                )
            }
            drawIndex += 1
            continue
        }
        // pipelineFor, not a direct pipelinesByFormat lookup: swaps in this format's
        // VK_POLYGON_MODE_LINE variant when Renderer.wireframe is on and one was built for
        // it, or its VK_CULL_MODE_BACK_BIT variant when drawCall.cullMode asks for it (see
        // that function's doc comment) -- an unregistered format still resolves to null and is
        // skipped below, same as before either parameter existed.
        val pipeline = pipelineFor(drawCall.mesh.format, drawCall.cullMode, drawCall.transparent)
        if (pipeline != null) {
            val material = drawCall.material as Material
            val uniformSlotIndex = materialUsage.nextSlot(drawCall.material)
            // Kotlin's `A * B` computes the conventional `B * A` (see Mat4.times/
            // Lens.viewProjectionMatrix's docs), so `model * viewProjection` (Kotlin
            // order) gives the conventional `projection * view * model`.
            val mvp = drawCall.model * viewProjection
            // Compared by FORMAT, not pipeline identity: wireframe's pipelineFor can resolve the
            // primary lit format to a different pipeline object than renderPipeline itself.
            val uniformFloats = uniformBlockFor(drawCall, mvp, cascades, frame)
            val binding =
                material.updateUniformBuffer(frameIndex, uniformSlotIndex, uniformFloats)
            prepared += PreparedDrawCall(
                mesh = drawCall.mesh as Mesh,
                isTransparent = drawCall.transparent,
                pipeline = pipeline,
                material = material,
                frameIndex = frameIndex,
                uniformSlotIndex = uniformSlotIndex,
                materialBinding = binding,
                depthSortKey = drawCall.model.squaredDistanceFrom(cameraPosition),
            )
        } else if (debugMode) {
            println(
                "Awake (Vulkan): DrawCall skipped -- no pipeline registered for mesh format " +
                    "${drawCall.mesh.format}.",
            )
        }
        drawIndex += 1
    }
    return prepared
}

/** One [DrawCall] carrying [instanceModels] resolved against
 * [Renderer.instancedPipelinesByFormat], or `null` when this format has no instanced pipeline
 * (or there is nothing to draw) -- see that field's own doc comment for why an unresolved
 * format is skipped rather than force-drawn.
 *
 * The uniform write is what genuinely differs from the non-instanced path: `instanced.wgsl`'s
 * `Uniforms` holds `viewProjection` + light, NOT a per-draw `mvp`, since one call covers many
 * model matrices and none of them can be folded in on the CPU. It's still written into
 * [DrawCall.material]'s own frame/draw slot (not a pipeline-owned buffer): the block is the same
 * 24 floats the non-shadow lit path already writes there, so no second uniform/descriptor scheme
 * is needed for it. [uniformFloats] is already the caller's fully-assembled block (particle vs.
 * light-based content decided by the caller, which already branches on [DrawCall.mesh]'s
 * format) -- this function just writes it, it doesn't need to know which case it is.
 *
 * A call that also carries [DrawCall.instanceJointPalettes] resolves against
 * [Renderer.skinnedInstancedPipelinesByFormat] instead and also
 * fills a [SkinnedInstanceBuffer] with this call's per-instance joint palettes -- everything
 * else (uniform block, model-matrix instance buffer, draw call) is identical, which is why
 * this stayed one function rather than a near-duplicate second one. */
private fun Renderer.prepareInstancedDrawCall(
    drawCall: DrawCall,
    frameIndex: Int,
    instancedIndex: Int,
    uniformFloats: FloatArray,
    materialUsage: MutableMap<RenderMaterial, Int>,
): PreparedDrawCall? {
    val kind = drawCall.instancedDrawKind()
    val pipeline = kind?.let { pipelines.resolveInstanced(drawCall.mesh.format, it) }
    // One guard, not two: `instancedDrawKind` already returns null for a draw with no instances,
    // and `resolveInstanced` for a flavour this app built no pipeline for. Either means "not
    // drawn" -- and detekt caps this function at two returns.
    if (kind == null || pipeline == null) return null
    val animated = kind == InstancedDrawKind.Skinned
    val isParticle = kind == InstancedDrawKind.Particle
    val instanceModels = drawCall.instanceModels.orEmpty()
    val material = drawCall.material as Material
    val uniformSlotIndex = materialUsage.nextSlot(drawCall.material)
    val instanceBuffer = instanceBufferForRun(instancedIndex)
    instanceBuffer.update(frameIndex, instanceModels)
    val jointPaletteBuffer = if (animated) {
        skinnedInstanceBufferForRun(instancedIndex).also {
            it.update(frameIndex, drawCall.instanceJointPalettes.orEmpty())
        }
    } else {
        null
    }
    val alphaInstanceBuffer = if (isParticle) {
        alphaInstanceBufferForRun(instancedIndex).also {
            it.update(frameIndex, drawCall.instanceColors.orEmpty())
        }
    } else {
        null
    }
    val frameInstanceBuffer = if (isParticle) {
        frameInstanceBufferForRun(instancedIndex).also {
            it.update(frameIndex, drawCall.instanceFrames.orEmpty())
        }
    } else {
        null
    }
    val binding = material.updateUniformBuffer(frameIndex, uniformSlotIndex, uniformFloats)
    // depthSortKey stays 0: an instanced draw is never transparent (PipelineRequest builds the
    // transparent companion only for the non-instanced primary/format requests), so it never
    // reaches the back-to-front sort.
    return PreparedDrawCall(
        mesh = drawCall.mesh as Mesh,
        isTransparent = drawCall.transparent,
        pipeline = pipeline,
        material = material,
        frameIndex = frameIndex,
        uniformSlotIndex = uniformSlotIndex,
        materialBinding = binding,
        instanceBuffer = instanceBuffer,
        instanceCount = instanceModels.size,
        jointPaletteBuffer = jointPaletteBuffer,
        alphaInstanceBuffer = alphaInstanceBuffer,
        frameInstanceBuffer = frameInstanceBuffer,
    )
}

/** The NDC depth one shadow-map texel of lateral travel spans on a 45-degree surface --
 * `lit_shadow.wgsl` scales its depth bias by this so its constants can be written in texels.
 * Computed here because this is where the light frustum's depth range and the map size both
 * live; a copy of them in the shader is how a bias silently ends up meaning ~40x more
 * world-space offset than it reads, which detaches the shadow from its caster. 0 with no shadow
 * map, where nothing reads it. */
/**
 * The uniform block one draw's shader expects, by the vertex format its pipeline resolved from.
 *
 * Its own function so [prepareDrawCalls] stays under detekt's complexity ceiling, and because the
 * choice is a single question -- which shader is this, and does it read a depth target -- rather
 * than three conditions spread through a loop body.
 *
 * The two PBR paths assemble their COMPLETE block through the shared writers, mvp included, which
 * both backends call; every other path still gets `mvp.data +` its own extras, one FloatArray
 * allocation more per draw.
 */
private fun Renderer.uniformBlockFor(
    drawCall: DrawCall,
    mvp: Mat4,
    cascades: ShadowCascadeUniforms?,
    frame: SceneFrameUniforms,
): FloatArray {
    // Compared by FORMAT, not pipeline identity: wireframe's pipelineFor can resolve the primary
    // lit format to a different pipeline object than renderPipeline itself.
    val isPrimaryFormat = drawCall.mesh.format == renderPipeline.vertexFormat
    // The UV layout identifies the textured PBR ABI even when an app makes it primary.
    // A format-specific pipeline is not required to be a secondary pipeline.
    val isTextured = drawCall.mesh.format == VertexFormat.PositionNormalColorUv
    return when {
        isTextured -> texturedUniforms(drawCall, mvp, frame)
        isPrimaryFormat && cascades != null -> litShadowUniforms(drawCall, mvp, cascades, frame)
        // Shadows off, but this renderer HAS a depth target -- so its primary pipeline is the
        // shadowed shader, and it reads the whole block whatever `shadowsEnabled` says. Writing
        // the short one here left material, camera position and fog reading stale buffer bytes,
        // which rendered as a scene that got darker when shadows were switched off.
        isPrimaryFormat && depthTarget != null ->
            litShadowUniforms(drawCall, mvp, UNSHADOWED_CASCADES, frame)
        // No depth target at all: the primary shader is the unshadowed one, whose block is the
        // light, directional only. Anything else supplies its own extras, e.g. a skinned mesh's
        // joint palette.
        isPrimaryFormat -> mvp.data + frame.light.directional
        else -> mvp.data + drawCall.extraUniformFloats
    }
}

private fun Renderer.shadowTexelDepthScale(): Float {
    val map = depthTarget ?: return 0f
    return directionalShadowTexelDepthScale(map.size)
}

private fun Renderer.fogFloats(
    environment: EnvironmentUniforms = EnvironmentUniforms(
        showSky = showEnvironment,
        horizonColor = horizonColor,
        zenithColor = zenithColor,
        fogDensity = fogDensity,
        fogColor = fogColor,
        shadowsEnabled = shadowsEnabled,
    ),
): FloatArray = fogUniformFloats(environment.fogColor, environment.fogDensity)

private fun MutableMap<RenderMaterial, Int>.nextSlot(material: RenderMaterial): Int {
    val slot = this[material] ?: 0
    this[material] = slot + 1
    return slot
}
