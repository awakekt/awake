/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.SortedDraws
import com.awakekt.awake.render.command.sortForRecording
import com.awakekt.awake.render.passes.instancedUniformFloats
import com.awakekt.awake.render.passes.uniformFloats
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.pipeline.batchKey
import com.awakekt.awake.render.pipeline.depthSortKey
import com.awakekt.awake.render.pipeline.instancedDrawKind
import com.awakekt.awake.render.pipeline.resolve
import com.awakekt.awake.render.pipeline.resolveInstanced
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.material.Material
import com.awakekt.awake.webgpu.mesh.Mesh
import com.awakekt.awake.webgpu.pipeline.WebGpuBindGroupHandle
import com.awakekt.awake.webgpu.pipeline.WebGpuPipelineHandle
import com.awakekt.awake.webgpu.pipeline.hasBindingGroup
import com.awakekt.awake.webgpu.pipeline.uniformByteSize
import io.ygdrasil.webgpu.GPUBuffer

internal fun Renderer.prepareGpuDraws(
    draws: List<GpuDrawRequest>,
    isTransparent: Boolean,
    primary: PrimaryPipelineBinding,
    viewProjection: Mat4,
    cameraEye: Vec3f,
    lightUniforms: FloatArray,
    shadowCascades: GpuShadowCascadeData? = null,
    fogColor: Color = Color.Black,
    fogDensity: Float = 0f,
    singleIndexStart: Int = 0,
): SortedDraws<PreparedDraw> {
    var singleIndex = singleIndexStart
    var instancedIndex = 0
    val prepared = buildList(draws.size) {
        for (cmd in draws) {
            prepareGpuDraw(
                cmd = cmd,
                singleIndex = singleIndex,
                instancedIndex = instancedIndex,
                isTransparent = isTransparent,
                primary = primary,
                viewProjection = viewProjection,
                cameraEye = cameraEye,
                lightUniforms = lightUniforms,
                shadowCascades = shadowCascades,
                fogColor = fogColor,
                fogDensity = fogDensity,
            )?.let {
                add(it)
                if (cmd.instanceModels != null) {
                    instancedIndex += 1
                } else {
                    singleIndex += 1
                }
            }
        }
    }
    return sortForRecording(prepared)
}

internal fun Renderer.prepareGpuDraw(
    cmd: GpuDrawRequest,
    singleIndex: Int,
    instancedIndex: Int,
    isTransparent: Boolean,
    primary: PrimaryPipelineBinding,
    viewProjection: Mat4,
    cameraEye: Vec3f,
    lightUniforms: FloatArray,
    shadowCascades: GpuShadowCascadeData? = null,
    fogColor: Color = Color.Black,
    fogDensity: Float = 0f,
): WebGpuPreparedDraw? {
    val mesh = cmd.mesh as Mesh
    val material = cmd.material as Material
    val instanceModels = cmd.instanceModels
    if (instanceModels != null) {
        return prepareInstancedGpuDraw(
            cmd = cmd,
            mesh = mesh,
            material = material,
            instanceModels = instanceModels,
            instanceIndex = instancedIndex,
            viewProjection = viewProjection,
            cameraEye = cameraEye,
            lightUniforms = lightUniforms,
            shadowCascades = shadowCascades,
            fogColor = fogColor,
            fogDensity = fogDensity,
            isTransparent = isTransparent,
        )
    }
    val pipeline = pipelines.resolve(
        format = mesh.format,
        cullMode = cmd.cullMode,
        transparent = isTransparent,
        wireframe = primary.wireframe,
    )?.handle ?: primary.pipeline
    val slot = bufferPools.uniformSlotForDraw(pipeline, singleIndex)
    val uniformFloats = cmd.uniformFloats(
        materialUniformFloatCount = material.uniformFloatCount,
        viewProjection = viewProjection,
        cameraEye = cameraEye,
        lightPayload = lightUniforms,
        shadowCascades = shadowCascades,
        fogColor = fogColor,
        fogDensity = fogDensity,
    )
    slot?.let {
        graphicsDevice.wgpuContext.device.queue.writeBuffer(
            it.buffer,
            0uL,
            fastArrayBufferOf(uniformFloats),
        )
    }
    return WebGpuPreparedDraw(
        pipeline = pipeline,
        materialBinding = when {
            slot == null -> EmptyWebGpuMaterialBinding
            material.hasTexture -> WebGpuBindGroupHandle(
                material.bindGroupFor(
                    pipeline.pipeline,
                    slot.buffer,
                    pipeline.materialBindings ?: com.awakekt.awake.render.pipeline.GroupBindings.StandardMaterial,
                ),
            )
            else -> slot.binding ?: EmptyWebGpuMaterialBinding
        },
        vertexBuffer = mesh.vertexBinding,
        indexBuffer = mesh.indexBinding,
        elementCount = mesh.indexCount,
        uniformBuffer = slot?.buffer,
        vertexFormat = mesh.format,
        transparent = isTransparent,
        shadowBinding = depthPrePass?.depthTarget
            ?.takeIf { pipeline.hasBindingGroup(pipeline.bindingLayout.slot(BindingSemantic.ShadowDepth)) }
            ?.let { bufferPools.shadowBindingFor(pipeline, it) },
        sceneDepthBinding = sceneDepthPass?.depthTarget
            ?.takeIf { pipeline.hasBindingGroup(pipeline.bindingLayout.slot(BindingSemantic.SceneDepth)) }
            ?.let {
                bufferPools.sceneDepthBindingFor(pipeline, it)
            },
        depthSortKey = cmd.depthSortKey(cameraEye),
        batchKey = cmd.batchKey(),
    )
}

private object EmptyWebGpuMaterialBinding : MaterialBinding

private fun Renderer.prepareInstancedGpuDraw(
    cmd: GpuDrawRequest,
    mesh: Mesh,
    material: Material,
    instanceModels: List<com.awakekt.awake.core.math.Mat4>,
    instanceIndex: Int,
    viewProjection: Mat4,
    cameraEye: Vec3f,
    lightUniforms: FloatArray,
    shadowCascades: GpuShadowCascadeData? = null,
    fogColor: Color = Color.Black,
    fogDensity: Float = 0f,
    isTransparent: Boolean,
): WebGpuPreparedDraw? {
    if (instanceModels.isEmpty()) return null
    val kind = cmd.instancedDrawKind() ?: return null
    val pipeline = pipelines.resolveInstanced(mesh.format, kind)?.handle ?: return null
    val instanceBuffer = instanceBufferForRun(instanceIndex).also { it.update(instanceModels) }
    val materialBinding: MaterialBinding
    val jointPaletteBinding: MaterialBinding?
    val jointPaletteBuffer: GPUBuffer?
    val shadowBinding = if (kind == InstancedDrawKind.Particle) {
        null
    } else {
        depthPrePass?.depthTarget
            ?.takeIf { pipeline.hasBindingGroup(pipeline.bindingLayout.slot(BindingSemantic.ShadowDepth)) }
            ?.let { bufferPools.shadowBindingFor(pipeline, it) }
    }
    val uniformFloats = cmd.instancedUniformFloats(
        kind = kind,
        viewProjection = viewProjection,
        lightPayload = lightUniforms,
        cameraEye = cameraEye,
        shadowCascades = shadowCascades,
        fogColor = fogColor,
        fogDensity = fogDensity,
        materialUniformFloatCount = maxOf(
            material.uniformFloatCount,
            (pipeline.uniformByteSize(0, 0) / Float.SIZE_BYTES).toInt(),
        ),
    )
    val plainUniformResources = if (kind == InstancedDrawKind.Plain && pipeline.hasBindingGroup(0)) {
        bufferPools.instancedUniformResources(pipeline)
    } else {
        null
    }
    val skinnedUniformResources = if (kind == InstancedDrawKind.Skinned && pipeline.hasBindingGroup(0)) {
        bufferPools.skinnedInstancedUniformResources(pipeline)
    } else {
        null
    }

    when (kind) {
        InstancedDrawKind.Particle -> {
            if (!material.hasTexture) return null
            material.updateUniformBuffer(uniformFloats)
            materialBinding = material.bindingFor(pipeline)
            jointPaletteBinding = null
            jointPaletteBuffer = null
        }

        InstancedDrawKind.Skinned -> {
            val resources = skinnedUniformResources ?: return null
            graphicsDevice.wgpuContext.device.queue.writeBuffer(
                resources.buffer,
                0uL,
                fastArrayBufferOf(uniformFloats),
            )
            materialBinding = resources.binding
            jointPaletteBinding = cmd.instanceJointPalettes?.let { palettes ->
                skinnedInstanceBufferForRun(instanceIndex).run {
                    update(palettes)
                    bindingFor(pipeline)
                }
            }
            jointPaletteBuffer = skinnedInstanceBufferForRun(instanceIndex).bufferRef()
        }

        InstancedDrawKind.Plain -> {
            val resources = plainUniformResources ?: return null
            graphicsDevice.wgpuContext.device.queue.writeBuffer(
                resources.buffer,
                0uL,
                fastArrayBufferOf(uniformFloats),
            )
            materialBinding = resources.binding
            jointPaletteBinding = null
            jointPaletteBuffer = null
        }
    }
    val particleColors = if (kind == InstancedDrawKind.Particle) {
        alphaInstanceBufferForRun(instanceIndex).also { it.update(cmd.instanceColors.orEmpty()) }.binding
    } else {
        null
    }
    val particleFrames = if (kind == InstancedDrawKind.Particle) {
        frameInstanceBufferForRun(instanceIndex).also { it.update(cmd.instanceFrames.orEmpty()) }.binding
    } else {
        null
    }
    return WebGpuPreparedDraw(
        pipeline = pipeline,
        materialBinding = materialBinding,
        vertexBuffer = mesh.vertexBinding,
        indexBuffer = mesh.indexBinding,
        elementCount = mesh.indexCount,
        instances = instanceModels.size,
        instanceVertexBuffer = instanceBuffer.binding,
        instanceVertexGpuBuffer = instanceBuffer.bufferRef(),
        jointPaletteBinding = jointPaletteBinding,
        jointPaletteGpuBuffer = jointPaletteBuffer,
        shadowBinding = shadowBinding,
        sceneDepthBinding = sceneDepthPass?.depthTarget
            ?.takeIf { pipeline.hasBindingGroup(pipeline.bindingLayout.slot(BindingSemantic.SceneDepth)) }
            ?.let {
                bufferPools.sceneDepthBindingFor(pipeline, it)
            },
        uniformBuffer = when (kind) {
            InstancedDrawKind.Plain -> plainUniformResources?.buffer
            InstancedDrawKind.Skinned -> skinnedUniformResources?.buffer
            InstancedDrawKind.Particle -> null
        },
        instanceColorBuffer = particleColors,
        instanceFrameBuffer = particleFrames,
        transparent = isTransparent,
        depthSortKey = cmd.depthSortKey(cameraEye),
        batchKey = cmd.batchKey(),
        vertexFormat = mesh.format,
    )
}

/** The primary pipeline and wireframe selection for a packet recording pass. */
internal class PrimaryPipelineBinding(
    val pipeline: WebGpuPipelineHandle,
    val wireframe: Boolean,
)

/** Backend-owned prepared draw consumed by the shared recording feature. */
internal class WebGpuPreparedDraw(
    override val pipeline: PipelineHandle,
    override val materialBinding: com.awakekt.awake.render.command.MaterialBinding,
    override val vertexBuffer: BufferHandle?,
    override val indexBuffer: BufferHandle?,
    override val elementCount: Int,
    override val instances: Int = 1,
    override val instanceVertexBuffer: BufferHandle? = null,
    override val jointPaletteBinding: com.awakekt.awake.render.command.MaterialBinding? = null,
    override val shadowBinding: com.awakekt.awake.render.command.MaterialBinding? = null,
    override val sceneDepthBinding: com.awakekt.awake.render.command.MaterialBinding? = null,
    internal val uniformBuffer: GPUBuffer? = null,
    internal val instanceVertexGpuBuffer: GPUBuffer? = null,
    internal val jointPaletteGpuBuffer: GPUBuffer? = null,
    override val vertexFormat: VertexFormat? = null,
    override val instanceColorBuffer: BufferHandle? = null,
    override val instanceFrameBuffer: BufferHandle? = null,
    override val transparent: Boolean = false,
    override val depthSortKey: Float = 0f,
    override val batchKey: Int = 0,
) : PreparedDraw
