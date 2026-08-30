/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu.renderer

import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.core.math.squaredDistanceFrom
import io.github.awakelab.awake.core.math.times
import io.github.awakelab.awake.render.command.BufferHandle
import io.github.awakelab.awake.render.command.MaterialBinding
import io.github.awakelab.awake.render.command.PipelineHandle
import io.github.awakelab.awake.render.command.PreparedDraw
import io.github.awakelab.awake.render.command.sortForRecording
import io.github.awakelab.awake.render.passes.uniforms.SceneFrameUniforms
import io.github.awakelab.awake.render.passes.uniforms.SceneLightUniforms
import io.github.awakelab.awake.render.passes.uniforms.litShadowUniforms
import io.github.awakelab.awake.render.passes.uniforms.texturedUniforms
import io.github.awakelab.awake.render.pipeline.InstancedDrawKind
import io.github.awakelab.awake.render.pipeline.instancedDrawKind
import io.github.awakelab.awake.render.pipeline.resolve
import io.github.awakelab.awake.render.pipeline.resolveInstanced
import io.github.awakelab.awake.render.renderer.DrawCall
import io.github.awakelab.awake.render.renderer.InstancedUniformLayout
import io.github.awakelab.awake.render.renderer.ParticleExtraUniformLayout
import io.github.awakelab.awake.render.renderer.ParticleUniformLayout
import io.github.awakelab.awake.render.renderer.UniformFields
import io.github.awakelab.awake.render.renderer.UniformWriter
import io.github.awakelab.awake.webgpu.fastArrayBufferOf
import io.github.awakelab.awake.webgpu.material.Material
import io.github.awakelab.awake.webgpu.mesh.Mesh
import io.github.awakelab.awake.webgpu.pipeline.RenderPipeline
import io.github.awakelab.awake.webgpu.pipeline.WebGpuPipelineHandle
import io.ygdrasil.webgpu.GPURenderPipeline

/**
 * The half of this backend's opaque pass that stays backend-specific: resolving each [DrawCall]
 * to a pipeline, writing its uniforms, filling its instance buffers, and naming the bind groups
 * it draws with. Recording those draws is `SharedOpaqueRenderFeature`'s job, once, for both
 * backends -- this file's output is its input.
 *
 * Runs BEFORE `beginRenderPass`, where the recording loop used to do all of this inline. Safe:
 * every write here is a `queue.writeBuffer`, which is queue-scheduled and therefore already
 * executed before the encoder this frame builds is submitted -- it never interleaved with
 * encoding in the first place (the ceiling `Renderer`'s own class doc comment describes).
 */
internal fun Renderer.prepareOpaqueDraws(
    drawCalls: List<DrawCall>,
    frame: FrameDrawContext,
    primary: PrimaryPipelineBinding,
): PreparedDraws {
    val prepared = ArrayList<PreparedDraw>(drawCalls.size)
    var instancedIndex = 0
    var singleIndex = 0
    var drawIndex = 0
    while (drawIndex < drawCalls.size) {
        val drawCall = drawCalls[drawIndex]
        val draw = if (drawCall.instanceModels != null) {
            prepareInstancedDraw(
                drawCall,
                frame.cameraEye,
                instancedIndex,
                frame.viewProjection,
                frame.lightUniforms,
            )
                ?.also { instancedIndex += 1 }
        } else {
            prepareSingleDraw(drawCall, frame, primary, singleIndex)
                ?.also { singleIndex += 1 }
        }
        if (draw != null) prepared += draw
        drawIndex += 1
    }
    // Shared with Vulkan -- partition, group opaque by pipeline, cluster each group by batchKey,
    // sort transparent back to front. This backend previously skipped the within-group
    // clustering; it now gets it.
    val sorted = sortForRecording(prepared)
    return PreparedDraws(opaque = sorted.opaqueByPipeline, transparent = sorted.transparent)
}

/** This frame's draws, split by how they must be ordered: opaque batched by pipeline, transparent
 * back-to-front. See [SharedTransparentRenderFeature] for why one list cannot serve both. */
internal data class PreparedDraws(
    val opaque: Map<PipelineHandle, List<PreparedDraw>>,
    val transparent: List<PreparedDraw>,
)

/** The primary (or wireframe) pipeline for this frame's primary-format draws -- resolved once by
 * `performDraw`. Each draw takes its own uniform buffer/bind group from the pool rather than
 * sharing one, so draws don't overwrite each other's MVP.
 *
 * [backCulled] is the `GPUCullMode.Back` companion, chosen per draw call by
 * `MeshRenderer.cullMode`; null when wireframe is active (a wireframe view shows both sides
 * regardless) or when none was built. */
/** Everything a draw needs that the FRAME decided, not the draw: the camera, its matrices,
 * this frame's lights, and the light's own view-projection for the shadow lookup. Bundled
 * because threading them one by one pushed three signatures past the parameter budget, and
 * they always travel together anyway. */
internal class FrameDrawContext(
    val cameraEye: Vec3f,
    val viewProjection: Mat4,
    val lightUniforms: SceneLightUniforms,
    /** Identity when this renderer has no depth target -- primaryDraw ignores it then. */
    val lightViewProjection: Mat4,
)

internal class PrimaryPipelineBinding(
    val pipeline: WebGpuPipelineHandle,
    val wireframe: Boolean,
)

private fun Renderer.prepareSingleDraw(
    drawCall: DrawCall,
    frame: FrameDrawContext,
    primary: PrimaryPipelineBinding,
    slotIndex: Int,
): PreparedDraw? {
    // A mesh whose format has neither the primary pipeline nor an additionalPipelines entry is
    // skipped rather than drawn through a pipeline that expects a different vertex layout,
    // matching Vulkan's Renderer.pipelinesByFormat skip-on-mismatch guard.
    val isPrimaryFormat = drawCall.mesh.format == primaryVertexFormat
    val extraPipeline = if (isPrimaryFormat) {
        null
    } else {
        // Precedence lives in PipelineTable.resolve, shared with Vulkan. Wireframe is false here
        // on purpose: an additional format has no LineList companion, so those keep drawing
        // filled while Renderer.wireframe is on (resolve would fall back to fill anyway, but
        // saying it here keeps the reason attached to the decision).
        pipelines.resolve(
            format = drawCall.mesh.format,
            cullMode = drawCall.cullMode,
            transparent = drawCall.transparent,
        )
    }
    val extraMaterial = (drawCall.material as? Material)?.takeIf { it.hasTexture }
    if (!isPrimaryFormat && (extraPipeline == null || extraMaterial == null)) {
        reportSkippedFormat(drawCall)
        return null
    }
    // Kotlin's `A * B` computes the conventional `B * A` (see Mat4.times/
    // Camera.viewProjectionMatrix's docs), matching vulkanMain's Renderer.
    val mvp = drawCall.model * frame.viewProjection
    return if (extraPipeline != null && extraMaterial != null) {
        texturedDraw(drawCall, extraPipeline, mvp, frame)
    } else {
        primaryDraw(drawCall, frame, mvp, primary, slotIndex)
    }
}

/** The material owns its uniform buffer + bind group. Its Uniforms is mvp + light + model +
 * cameraPosition (textured.wgsl's PBR specular needs a world-space position and view vector) --
 * the same 44 floats vulkanMain's `prepareDrawCalls` writes for this format. Wireframe has no
 * companion pipeline per additional format, so these stay filled (same fallback as Vulkan's
 * `pipelineFor`). */
private fun Renderer.texturedDraw(
    drawCall: DrawCall,
    pipeline: RenderPipeline,
    mvp: Mat4,
    frame: FrameDrawContext,
): PreparedDraw {
    val material = drawCall.material as Material
    material.updateUniformBuffer(
        texturedUniforms(drawCall, mvp, SceneFrameUniforms(frame.lightUniforms, frame.cameraEye, fogFloats())),
    )
    val mesh = drawCall.mesh as Mesh
    return WebGpuPreparedDraw(
        pipeline = pipeline.handle,
        materialBinding = material.bindingFor(pipeline.handle.pipeline),
        vertexBuffer = mesh.vertexBinding,
        indexBuffer = mesh.indexBinding,
        elementCount = mesh.indexCount,
        transparent = drawCall.transparent,
        depthSortKey = drawCall.model.squaredDistanceFrom(frame.cameraEye),
        batchKey = drawCall.mesh.hashCode(),
    )
}

/** Wireframe only applies here: an additional pipeline has no `LineList` companion, so feeding it
 * a mesh's line index buffer would hand line indices to a `TriangleList` pipeline. A
 * `CullMode.Back` draw swaps to [PrimaryPipelineBinding.backCulled] when one was built, falling
 * back to the filled primary otherwise -- same "nothing to switch to, keep drawing" shape as
 * wireframe. */
private fun Renderer.primaryDraw(
    drawCall: DrawCall,
    frame: FrameDrawContext,
    mvp: Mat4,
    primary: PrimaryPipelineBinding,
    slotIndex: Int,
): PreparedDraw {
    // Same shared precedence the non-primary path uses. `primary.pipeline` is the fallback for
    // a resolve that finds nothing, which cannot happen for the primary format but keeps this
    // total.
    val pipeline = pipelines.resolve(
        format = drawCall.mesh.format,
        cullMode = drawCall.cullMode,
        transparent = drawCall.transparent,
        wireframe = primary.wireframe,
    )?.handle ?: primary.pipeline
    // This draw's own slot, not a Renderer-wide buffer: every primary-format draw writes its own
    // mvp, and queue.writeBuffer is queue-scheduled, so a shared buffer would leave every draw
    // rendering with whichever mvp was written last.
    val slot = bufferPools.uniformSlotForDraw(pipeline.pipeline, slotIndex)
    // Shadowed renderers write lit_shadow.wgsl's whole block (the shared packer both backends
    // use); everyone else writes the 24-float primary block into the same, larger slot.
    val shadowTarget = depthPrePass?.depthTarget
    val uniformFloats = if (shadowTarget != null) {
        litShadowUniforms(
            drawCall = drawCall,
            mvp = mvp,
            lightMvp = drawCall.model * frame.lightViewProjection,
            frame = SceneFrameUniforms(frame.lightUniforms, frame.cameraEye, fogFloats()),
        )
    } else {
        UniformWriter(InstancedUniformLayout)
            .put(mvp.data, UniformFields.Mvp)
            .let(frame.lightUniforms::writeDirectionalTo)
            .build()
    }
    graphicsDevice.wgpuContext.device.queue.writeBuffer(
        slot.buffer,
        0uL,
        fastArrayBufferOf(uniformFloats),
    )
    val mesh = drawCall.mesh as Mesh
    val lineIndices = primary.wireframe
    return WebGpuPreparedDraw(
        pipeline = pipeline,
        materialBinding = slot.binding,
        shadowBinding = shadowTarget?.let { bufferPools.shadowBindingFor(pipeline, it) },
        sceneDepthBinding = sceneDepthPass?.depthTarget
            ?.let { bufferPools.sceneDepthBindingFor(pipeline, it) },
        vertexBuffer = mesh.vertexBinding,
        indexBuffer = if (lineIndices) mesh.lineIndexBinding else mesh.indexBinding,
        elementCount = if (lineIndices) mesh.lineIndexCount else mesh.indexCount,
        transparent = drawCall.transparent,
        depthSortKey = drawCall.model.squaredDistanceFrom(frame.cameraEye),
        batchKey = drawCall.mesh.hashCode(),
    )
}

/** One uniform write and one instanced draw for every transform, instead of a per-draw mvp.
 * Animated instancing (skinned_instanced.wgsl) resolves against its own pipeline map and
 * additionally binds a per-instance joint-palette storage buffer at group 1; particles carry a
 * real textured material and their own camera-basis uniform block (particle.wgsl). */
private fun Renderer.prepareInstancedDraw(
    drawCall: DrawCall,
    cameraEye: Vec3f,
    instancedIndex: Int,
    viewProjection: Mat4,
    lightUniforms: SceneLightUniforms,
): PreparedDraw? {
    val instanceModels = drawCall.instanceModels.orEmpty()
    val kind = drawCall.instancedDrawKind()
    val isParticle = kind == InstancedDrawKind.Particle
    val instancedPipeline = kind?.let { pipelines.resolveInstanced(drawCall.mesh.format, it) }
    val particleMaterial = (drawCall.material as? Material)?.takeIf { isParticle && it.hasTexture }
    if (instancedPipeline == null || (isParticle && particleMaterial == null)) {
        reportSkippedInstanced(drawCall)
        return null
    }
    val resolved = requireNotNull(instancedPipeline).handle
    val mesh = drawCall.mesh as Mesh
    return WebGpuPreparedDraw(
        pipeline = resolved,
        materialBinding = instancedMaterialBinding(
            drawCall,
            particleMaterial,
            viewProjection,
            lightUniforms,
            resolved.pipeline,
        ),
        vertexBuffer = mesh.vertexBinding,
        indexBuffer = mesh.indexBinding,
        elementCount = mesh.indexCount,
        instances = instanceModels.size,
        instanceVertexBuffer = instanceBufferForRun(instancedIndex)
            .also { it.update(instanceModels) }.binding,
        jointPaletteBinding = jointPaletteBinding(drawCall, instancedIndex, resolved.pipeline),
        instanceColorBuffer = particleMaterial?.let {
            alphaInstanceBufferForRun(instancedIndex)
                .also { buffer -> buffer.update(drawCall.instanceColors.orEmpty()) }.binding
        },
        instanceFrameBuffer = particleMaterial?.let {
            frameInstanceBufferForRun(instancedIndex)
                .also { buffer -> buffer.update(drawCall.instanceFrames.orEmpty()) }.binding
        },
        transparent = drawCall.transparent,
        depthSortKey = drawCall.model.squaredDistanceFrom(cameraEye),
        batchKey = drawCall.mesh.hashCode(),
    )
}

/** Writes this instanced draw's group-0 uniform block and names the group that binds it. Three
 * cases, three separately-owned bind groups -- see `ensureInstancedUniformResources`' own doc
 * comment for why they can't be one. */
private fun Renderer.instancedMaterialBinding(
    drawCall: DrawCall,
    particleMaterial: Material?,
    viewProjection: Mat4,
    lightUniforms: SceneLightUniforms,
    resolved: GPURenderPipeline,
): MaterialBinding {
    val queue = graphicsDevice.wgpuContext.device.queue
    return when {
        particleMaterial != null -> {
            // Particles bind their own material's group (uniform + texture + sampler), sized for
            // particle.wgsl's viewProjection(16) + cameraRight(4) + cameraUp(4) block, not the
            // Renderer-shared viewProjection+light one.
            particleMaterial.updateUniformBuffer(
                UniformWriter(ParticleUniformLayout)
                    .put(viewProjection.data, UniformFields.Mvp)
                    .put(drawCall.extraUniformFloats, *ParticleExtraUniformLayout.fields)
                    .build(),
            )
            particleMaterial.bindingFor(resolved)
        }

        drawCall.instanceJointPalettes != null -> {
            ensureSkinnedInstancedUniformResources(resolved)
            queue.writeBuffer(
                skinnedInstancedUniformBuffer!!,
                0uL,
                fastArrayBufferOf(
                    UniformWriter(InstancedUniformLayout)
                        .put(viewProjection.data, UniformFields.Mvp)
                        .let(lightUniforms::writeDirectionalTo)
                        .build(),
                ),
            )
            skinnedInstancedUniformBinding!!
        }

        else -> {
            ensureInstancedUniformResources(resolved)
            queue.writeBuffer(
                instancedUniformBuffer!!,
                0uL,
                fastArrayBufferOf(
                    UniformWriter(InstancedUniformLayout)
                        .put(viewProjection.data, UniformFields.Mvp)
                        .let(lightUniforms::writeDirectionalTo)
                        .build(),
                ),
            )
            instancedUniformBinding!!
        }
    }
}

private fun Renderer.jointPaletteBinding(
    drawCall: DrawCall,
    instancedIndex: Int,
    resolved: GPURenderPipeline,
): MaterialBinding? = drawCall.instanceJointPalettes?.let { palettes ->
    skinnedInstanceBufferForRun(instancedIndex).run {
        update(palettes)
        bindingFor(resolved)
    }
}

private fun Renderer.reportSkippedFormat(drawCall: DrawCall) {
    if (!debugMode) return
    println(
        "Awake (WebGPU): DrawCall skipped -- no pipeline registered for mesh format " +
            "${drawCall.mesh.format}.",
    )
}

private fun Renderer.reportSkippedInstanced(drawCall: DrawCall) {
    if (!debugMode) return
    val skinned = if (drawCall.instanceJointPalettes != null) "skinned-" else ""
    println(
        "Awake (WebGPU): instanced DrawCall skipped -- no ${skinned}instanced pipeline " +
            "registered for mesh format ${drawCall.mesh.format}, or instanceModels was empty, " +
            "or the particle material had no texture.",
    )
}

/** This backend's [PreparedDraw]: a plain per-draw carrier, unlike Vulkan's, which already had a
 * per-frame prepared type to hang the port's members off. */
internal class WebGpuPreparedDraw(
    override val pipeline: PipelineHandle,
    override val materialBinding: MaterialBinding,
    override val vertexBuffer: BufferHandle?,
    override val indexBuffer: BufferHandle?,
    override val elementCount: Int,
    override val instances: Int = 1,
    override val instanceVertexBuffer: BufferHandle? = null,
    override val jointPaletteBinding: MaterialBinding? = null,
    override val shadowBinding: MaterialBinding? = null,
    override val sceneDepthBinding: MaterialBinding? = null,
    override val instanceColorBuffer: BufferHandle? = null,
    override val instanceFrameBuffer: BufferHandle? = null,
    override val transparent: Boolean = false,
    /** Squared distance from the camera, computed once at prepare time where the model matrix is
     * in scope. Squared and unrooted -- it is a sort key, not a distance. */
    override val depthSortKey: Float = 0f,
    /** Mesh identity, so the shared sort can cluster draws sharing a mesh within one pipeline
     * group and reuse its buffer bindings. */
    override val batchKey: Int = 0,
) : PreparedDraw
