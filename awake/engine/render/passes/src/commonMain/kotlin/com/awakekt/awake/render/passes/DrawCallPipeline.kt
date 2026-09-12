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
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey

enum class InstancedDrawKind {
    Plain,
    Skinned,
    Particle,
}

/** Shared classification for source packets and backend adapters. */
fun RenderDrawCommand.instancedDrawKind(): InstancedDrawKind? =
    resolveInstancedDrawKind(mesh.format, instanceModels, instanceJointPalettes)

/** Shared source-packet identity used to select the matching depth caster on both backends. */
fun RenderDrawCommand.depthRenderKey(): DepthRenderKey = DepthRenderKey(
    kind = when {
        instanceModels.isNullOrEmpty() && mesh.format == VertexFormat.PositionNormalColorSkin ->
            DepthCasterKind.Skinned
        !instanceModels.isNullOrEmpty() && instanceJointPalettes != null ->
            DepthCasterKind.SkinnedInstanced
        !instanceModels.isNullOrEmpty() && mesh.format == VertexFormat.PositionUv ->
            DepthCasterKind.Particle
        !instanceModels.isNullOrEmpty() -> DepthCasterKind.Instanced
        else -> DepthCasterKind.Ordinary
    },
    alphaMode = alphaMode,
)

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

fun <P> PipelineTable<P>.resolveInstanced(format: VertexFormat, kind: InstancedDrawKind): P? =
    when (kind) {
        InstancedDrawKind.Skinned -> skinnedInstancedByFormat[format]
        InstancedDrawKind.Particle -> particlePipelines[format]
        InstancedDrawKind.Plain -> instancedByFormat[format]
    }

/** Calculates the squared distance from this draw's model transform to the camera eye. */
fun RenderDrawCommand.depthSortKey(cameraEye: Vec3f): Float =
    model.squaredDistanceFrom(cameraEye)

/** Returns a clustering key (mesh hash) for consecutive buffer reuse. */
fun RenderDrawCommand.batchKey(): Int =
    mesh.hashCode()
