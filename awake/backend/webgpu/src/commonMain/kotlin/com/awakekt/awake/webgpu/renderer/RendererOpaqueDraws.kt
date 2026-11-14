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
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.mesh.GpuMesh
import com.awakekt.awake.render.passes.instancedUniformFloats
import com.awakekt.awake.render.passes.uniformFloats
import com.awakekt.awake.render.passes.uniforms.drawBlockFloats
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.pipeline.batchKey
import com.awakekt.awake.render.pipeline.depthSortKey
import com.awakekt.awake.render.pipeline.instancedDrawKind
import com.awakekt.awake.render.pipeline.resolve
import com.awakekt.awake.render.pipeline.resolveInstanced
import com.awakekt.awake.webgpu.material.Material
import com.awakekt.awake.webgpu.mesh.Mesh
import com.awakekt.awake.webgpu.mesh.sceneElementCount
import com.awakekt.awake.webgpu.mesh.sceneIndexBinding
import com.awakekt.awake.webgpu.mesh.sceneVertexBinding
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.pipeline.WebGpuBindGroupHandle
import com.awakekt.awake.webgpu.pipeline.WebGpuPipelineHandle
import com.awakekt.awake.webgpu.pipeline.hasBindingGroup
import com.awakekt.awake.webgpu.pipeline.texturedMaterialBindings
import com.awakekt.awake.webgpu.pipeline.uniformByteSize
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUPrimitiveTopology

internal fun Renderer.prepareGpuDraw(
    cmd: GpuDrawRequest,
    singleIndex: Int,
    instancedIndex: Int,
    isTransparent: Boolean,
    primary: PrimaryPipelineBinding,
    viewProjection: Mat4,
    cameraEye: Vec3f,
    cameraForward: Vec3f = Vec3f(0f, 0f, -1f),
    lightUniforms: FloatArray,
    shadowCascades: GpuShadowCascadeData? = null,
    fogColor: Color = Color.Black,
    fogDensity: Float = 0f,
    debugView: GpuDebugView = GpuDebugView.Off,
    exposure: Float = 1f,
    edgePipeline: RenderPipeline? = null,
    frameVersion: Int = NO_FRAME_VERSION,
): WebGpuPreparedDraw? {
    val mesh = cmd.mesh
    val material = cmd.material as Material
    val instanceModels = cmd.instanceModels
    if (instanceModels != null) {
        return prepareInstancedGpuDraw(
            cmd = cmd,
            // Only a built mesh is instanced; a dynamic one is always drawn once.
            mesh = mesh as Mesh,
            material = material,
            instanceModels = instanceModels,
            instanceIndex = instancedIndex,
            viewProjection = viewProjection,
            cameraEye = cameraEye,
            cameraForward = cameraForward,
            lightUniforms = lightUniforms,
            shadowCascades = shadowCascades,
            fogColor = fogColor,
            fogDensity = fogDensity,
            debugView = debugView,
            exposure = exposure,
            isTransparent = isTransparent,
            frameVersion = frameVersion,
        )
    }
    val resolved = edgePipeline ?: pipelines.resolve(
        format = mesh.format,
        cullMode = cmd.cullMode,
        transparent = isTransparent,
        wireframe = primary.wireframe,
        additive = cmd.additive,
        depthWrite = cmd.depthWrite,
    )
    val pipeline = resolved?.handle ?: primary.pipeline
    // A line-list pipeline, the wireframe view's or an edge overlay's, takes its indices in pairs:
    // it draws the mesh's line indices, since its triangle indices would join the wrong corners.
    val lines = resolved?.topology == GPUPrimitiveTopology.LineList
    val slot = bufferPools.uniformSlotForDraw(pipeline, singleIndex, edges = edgePipeline != null)
    slot?.let {
        if (pipeline.splitDrawBlockFloats > 0) {
            stageSplitDrawBlock(it, cmd, pipeline.splitDrawBlockFloats, (resolved ?: renderPipeline).multisample.alphaToCoverageEnabled)
        } else {
            stageDrawBlock(
                it, cmd, material.uniformFloatCount, (resolved ?: renderPipeline).multisample.alphaToCoverageEnabled, frameVersion,
                viewProjection, cameraEye, cameraForward, lightUniforms, shadowCascades, fogColor, fogDensity, debugView, exposure,
            )
        }
    }
    return WebGpuPreparedDraw(
        pipeline = pipeline,
        materialBinding = when {
            slot == null -> EmptyWebGpuMaterialBinding
            material.hasTexture -> WebGpuBindGroupHandle(
                material.bindGroupFor(pipeline.pipeline, slot.buffer, pipeline.texturedMaterialBindings, edges = edgePipeline != null),
            )
            else -> slot.binding ?: untexturedMaterialError(mesh, material)
        },
        vertexBuffer = mesh.sceneVertexBinding(),
        indexBuffer = mesh.sceneIndexBinding(lines),
        elementCount = mesh.sceneElementCount(lines),
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
        sortOrder = cmd.sortOrder,
        sortLayer = cmd.sortLayer,
        batchKey = cmd.batchKey(),
    )
}

private object EmptyWebGpuMaterialBinding : MaterialBinding

/**
 * The pipeline's group 0 samples a base-colour texture and [material] has none, as for an untextured
 * material on a `PositionNormalColorUv` mesh: `textured.wgsl` cannot draw it. Said here, naming
 * both, rather than as a cast failure when the recorder binds the group.
 */
private fun untexturedMaterialError(mesh: GpuMesh, material: Material): Nothing =
    error(
        "Material ${material.identity()} has no base-colour texture, but mesh ${mesh.identity()} " +
            "(${mesh.format}) draws through a textured pipeline whose group 0 samples one. " +
            "Give the material a texture, or use a mesh format without UVs.",
    )

private fun Any.identity(): String = "${this::class.simpleName}@${hashCode().toString(16)}"

/** Packs and stages [cmd]'s block into [slot], unless the slot already holds it, packed from the same inputs. */
@Suppress("LongParameterList") // prepareGpuDraw's, passed through.
private fun Renderer.stageDrawBlock(
    slot: UniformSlot,
    cmd: GpuDrawRequest,
    materialUniformFloatCount: Int,
    coverage: Boolean,
    frameVersion: Int,
    viewProjection: Mat4,
    cameraEye: Vec3f,
    cameraForward: Vec3f,
    lightUniforms: FloatArray,
    shadowCascades: GpuShadowCascadeData?,
    fogColor: Color,
    fogDensity: Float,
    debugView: GpuDebugView,
    exposure: Float,
) {
    if (slot.inputs.matches(cmd, frameVersion, materialUniformFloatCount, coverage)) return
    val uniformFloats = cmd.uniformFloats(
        materialUniformFloatCount = materialUniformFloatCount,
        viewProjection = viewProjection,
        cameraEye = cameraEye,
        cameraForward = cameraForward,
        lightPayload = lightUniforms,
        shadowCascades = shadowCascades,
        fogColor = fogColor,
        fogDensity = fogDensity,
        debugView = debugView,
        exposure = exposure,
        alphaToCoverageEnabled = coverage,
    )
    uniformUploads.packedBlocks += 1
    uniformUploads.stageChanged(slot, uniformFloats)
    slot.inputs.record(cmd, frameVersion, materialUniformFloatCount, coverage)
}

/**
 * Stages [cmd]'s own block for a pipeline that reads the frame block beside it, unless [slot]
 * already holds it. Nothing in this block is the camera's or the light's, so a draw that holds
 * still stages nothing while they move. An [instanced] draw's copies carry their transforms.
 */
private fun Renderer.stageSplitDrawBlock(slot: UniformSlot, cmd: GpuDrawRequest, drawBlockFloats: Int, coverage: Boolean, instanced: Boolean = false) {
    val kind = if (instanced) SPLIT_INSTANCED_INPUTS else SPLIT_INPUTS
    if (slot.inputs.matches(cmd, SPLIT_FRAME_VERSION, drawBlockFloats, coverage, kind)) return
    uniformUploads.packedBlocks += 1
    uniformUploads.stageChanged(slot, cmd.drawBlockFloats(drawBlockFloats, coverage, instanced))
    slot.inputs.record(cmd, SPLIT_FRAME_VERSION, drawBlockFloats, coverage, kind)
}

private fun Renderer.prepareInstancedGpuDraw(
    cmd: GpuDrawRequest,
    mesh: Mesh,
    material: Material,
    instanceModels: List<com.awakekt.awake.core.math.Mat4>,
    instanceIndex: Int,
    viewProjection: Mat4,
    cameraEye: Vec3f,
    cameraForward: Vec3f,
    lightUniforms: FloatArray,
    shadowCascades: GpuShadowCascadeData? = null,
    fogColor: Color = Color.Black,
    fogDensity: Float = 0f,
    debugView: GpuDebugView = GpuDebugView.Off,
    exposure: Float = 1f,
    isTransparent: Boolean,
    frameVersion: Int,
): WebGpuPreparedDraw? {
    if (instanceModels.isEmpty()) return null
    val kind = cmd.instancedDrawKind() ?: return null
    val resolved = pipelines.resolveInstanced(mesh.format, kind, cmd.additive, cmd.cullMode) ?: return null
    val pipeline = resolved.handle
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
    val materialUniformFloatCount = maxOf(
        material.uniformFloatCount,
        (pipeline.uniformByteSize(0, 0) / Float.SIZE_BYTES).toInt(),
    )
    val coverage = resolved.multisample.alphaToCoverageEnabled
    // Its own slot per instanced draw, as an ordinary draw has: one buffer shared by every
    // instanced draw of a pipeline, or by every particle draw of a material, would hold only the
    // last draw's uniforms by submission.
    val drawSlot = if (kind != InstancedDrawKind.Skinned) bufferPools.uniformSlotForDraw(pipeline, instanceIndex) else null
    // A slot already holding this draw's block, packed from the same inputs, needs no block; the
    // skinned draws share one buffer, so theirs is always packed.
    val inputKind = INSTANCED_INPUTS + kind.ordinal
    // A pipeline that reads the frame block takes the draw's own block instead, staged per slot below.
    val split = pipeline.splitDrawBlockFloats
    val packs = split == 0 && (drawSlot == null || !drawSlot.inputs.matches(cmd, frameVersion, materialUniformFloatCount, coverage, inputKind))
    val uniformFloats = if (split > 0 && kind == InstancedDrawKind.Skinned) {
        cmd.drawBlockFloats(split, coverage, instanced = true)
    } else if (!packs) {
        null
    } else {
        cmd.instancedUniformFloats(
            kind = kind,
            viewProjection = viewProjection,
            lightPayload = lightUniforms,
            cameraEye = cameraEye,
            cameraForward = cameraForward,
            shadowCascades = shadowCascades,
            fogColor = fogColor,
            fogDensity = fogDensity,
            debugView = debugView,
            exposure = exposure,
            materialUniformFloatCount = materialUniformFloatCount,
            alphaToCoverageEnabled = coverage,
        )
    }
    fun stageFor(slot: UniformSlot) {
        if (split > 0) return stageSplitDrawBlock(slot, cmd, split, coverage, instanced = true)
        uniformFloats ?: return
        uniformUploads.packedBlocks += 1
        uniformUploads.stageChanged(slot, uniformFloats)
        slot.inputs.record(cmd, frameVersion, materialUniformFloatCount, coverage, inputKind)
    }
    val skinnedUniformResources = if (kind == InstancedDrawKind.Skinned && pipeline.hasBindingGroup(0)) {
        bufferPools.skinnedInstancedUniformResources(pipeline)
    } else {
        null
    }

    when (kind) {
        InstancedDrawKind.Particle -> {
            val slot = drawSlot?.takeIf { material.hasTexture } ?: return null
            stageFor(slot)
            materialBinding = WebGpuBindGroupHandle(
                material.bindGroupFor(pipeline.pipeline, slot.buffer, pipeline.texturedMaterialBindings),
            )
            jointPaletteBinding = null
            jointPaletteBuffer = null
        }

        InstancedDrawKind.Skinned -> {
            val resources = skinnedUniformResources ?: return null
            uniformUploads.stage(resources.buffer, checkNotNull(uniformFloats))
            materialBinding = resources.binding
            jointPaletteBinding = cmd.instanceJointPalettes?.let { palettes ->
                skinnedInstanceBufferForRun(instanceIndex).run {
                    update(palettes, cmd.instanceColors)
                    bindingFor(pipeline)
                }
            }
            jointPaletteBuffer = skinnedInstanceBufferForRun(instanceIndex).bufferRef()
        }

        InstancedDrawKind.Plain -> {
            val slot = drawSlot ?: return null
            stageFor(slot)
            materialBinding = if (material.hasTexture) {
                WebGpuBindGroupHandle(
                    material.bindGroupFor(
                        pipeline.pipeline,
                        slot.buffer,
                        pipeline.texturedMaterialBindings,
                    ),
                )
            } else {
                slot.binding ?: untexturedMaterialError(mesh, material)
            }
            jointPaletteBinding = null
            jointPaletteBuffer = null
        }
    }
    // A tinted pipeline reads a tint per instance whatever the draw carries: white where it carries none.
    val particleColors = when {
        kind == InstancedDrawKind.Particle ->
            alphaInstanceBufferForRun(instanceIndex).also { it.update(cmd.instanceColors.orEmpty()) }.binding
        kind == InstancedDrawKind.Plain && resolved.readsInstanceTint -> alphaInstanceBufferForRun(instanceIndex).also { buffer ->
            cmd.instanceColors?.let(buffer::update) ?: buffer.fillWhite()
        }.binding
        else -> null
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
        // The depth pass binds this for the draw's shadow, so a particle reports its own slot too.
        uniformBuffer = when (kind) {
            InstancedDrawKind.Plain, InstancedDrawKind.Particle -> drawSlot?.buffer
            InstancedDrawKind.Skinned -> skinnedUniformResources?.buffer
        },
        instanceColorBuffer = particleColors,
        instanceFrameBuffer = particleFrames,
        transparent = isTransparent,
        depthSortKey = cmd.depthSortKey(cameraEye),
        sortOrder = cmd.sortOrder,
        sortLayer = cmd.sortLayer,
        batchKey = cmd.batchKey(),
        vertexFormat = mesh.format,
    )
}

/** No frame: a draw prepared with it is always packed. */
internal const val NO_FRAME_VERSION = -2

/** Instanced draws are told apart from ordinary ones in a slot's inputs, kind by kind, from here. */
private const val INSTANCED_INPUTS = 1

/** Input kinds of a draw's own block beside the frame block, apart from every whole-block kind. */
private const val SPLIT_INPUTS = 16
private const val SPLIT_INSTANCED_INPUTS = 17

/** A split draw block holds nothing of the frame's, so every frame is the same frame to it. */
private const val SPLIT_FRAME_VERSION = 0

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
    override val sortOrder: Int = 0,
    override val sortLayer: Int = 0,
    override val batchKey: Int = 0,
) : PreparedDraw
