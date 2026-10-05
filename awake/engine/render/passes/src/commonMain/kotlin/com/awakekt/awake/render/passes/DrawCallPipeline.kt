/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.squaredDistanceFrom
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey

/**
 * The classification of instanced draw call variants supported across pipelines.
 */
enum class InstancedDrawKind {
    /** Plain rigid instanced rendering with transform matrices. */
    Plain,

    /** Skeletally animated instanced rendering with transform matrices and joint palettes. */
    Skinned,

    /** Billboarded or planar particle instancing. */
    Particle,
}

/** Shared classification for source packets and backend adapters. */
fun RenderDrawCommand.instancedDrawKind(): InstancedDrawKind? =
    resolveInstancedDrawKind(mesh.format, instanceModels, instanceJointPalettes)

/** Shared source-packet identity used to select the matching depth caster on both backends. */
fun RenderDrawCommand.depthRenderKey(): DepthRenderKey = DepthRenderKey(
    kind = when {
        instanceModels.isNullOrEmpty() && mesh.format.isSkinned -> DepthCasterKind.Skinned
        !instanceModels.isNullOrEmpty() && instanceJointPalettes != null ->
            DepthCasterKind.SkinnedInstanced
        !instanceModels.isNullOrEmpty() && mesh.format == VertexFormat.PositionUv ->
            DepthCasterKind.Particle
        !instanceModels.isNullOrEmpty() -> DepthCasterKind.Instanced
        else -> DepthCasterKind.Ordinary
    },
    alphaMode = alphaMode,
)

/**
 * Resolves the [InstancedDrawKind] given a vertex [format], optional list of [instanceModels],
 * and optional list of [instanceJointPalettes].
 *
 * Returns `null` if [instanceModels] is null or empty (indicating a non-instanced draw call).
 */
fun resolveInstancedDrawKind(
    format: VertexFormat,
    instanceModels: List<Mat4>?,
    instanceJointPalettes: List<FloatArray>?,
): InstancedDrawKind? = when {
    instanceModels.isNullOrEmpty() -> null
    instanceJointPalettes != null -> InstancedDrawKind.Skinned
    format == VertexFormat.PositionUv -> InstancedDrawKind.Particle
    else -> InstancedDrawKind.Plain
}

/**
 * The pipeline an instanced draw of [kind] resolves to; [additive] picks a particle's additive twin
 * when built, and a plain draw with [cullMode] `Back` its back-culled twin.
 */
fun <P> PipelineTable<P>.resolveInstanced(
    format: VertexFormat,
    kind: InstancedDrawKind,
    additive: Boolean = false,
    cullMode: CullMode = CullMode.None,
): P? =
    when (kind) {
        InstancedDrawKind.Skinned -> skinnedInstancedByFormat[format]
        InstancedDrawKind.Particle -> additiveParticlePipelines[format]?.takeIf { additive } ?: particlePipelines[format]
        InstancedDrawKind.Plain ->
            instancedBackCulledByFormat[format]?.takeIf { cullMode == CullMode.Back } ?: instancedByFormat[format]
    }

/** Whether this table can draw instanced copies of a [format] mesh with [cullMode], as [resolveInstanced] would. */
fun <P> PipelineTable<P>.canInstance(format: VertexFormat, cullMode: CullMode): Boolean = when (cullMode) {
    CullMode.None -> format in instancedByFormat
    CullMode.Back -> format in instancedBackCulledByFormat
    CullMode.Front -> false
}

/** Calculates the squared distance from this draw's model transform to the camera eye. */
fun RenderDrawCommand.depthSortKey(cameraEye: Vec3f): Float =
    model.squaredDistanceFrom(cameraEye)

/** Returns a clustering key (mesh hash) for consecutive buffer reuse. */
fun RenderDrawCommand.batchKey(): Int =
    mesh.hashCode()
