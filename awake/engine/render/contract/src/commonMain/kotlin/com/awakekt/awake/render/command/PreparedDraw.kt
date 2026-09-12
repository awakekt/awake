/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.DepthRenderKey

/**
 * One draw, with every GPU handle already resolved -- what shared render-feature code iterates.
 * It is contract-owned because it represents the resolved driver input; render passes only decide
 * which already-prepared commands to record and in what order.
 *
 * [CommandRecorder] alone can't express a draw: it says how to issue a bind, not what to bind.
 * This is the other half of that boundary, and the same "opaque, backend-defined" rule applies to
 * every member -- each is a handle a backend already produced, passed through without inspection.
 *
 * An interface, not a data class, so each backend's own already-per-frame prepared type
 * (Vulkan's `PreparedDrawCall`) implements it directly instead of being copied into a second
 * object per draw per frame. Member names deliberately avoid the ones those types already use.
 */
interface PreparedDraw {
    val pipeline: PipelineHandle

    /** Vertex layout required by depth and feature variants. Null is allowed for custom
     * consumer pipelines that do not participate in engine-owned depth passes. */
    val vertexFormat: VertexFormat? get() = null

    /** Optional depth-only variant and bindings prepared alongside the primary draw. */
    val depthPipeline: PipelineHandle? get() = null
    val depthMaterialBinding: MaterialBinding? get() = null
    val depthJointPaletteBinding: MaterialBinding? get() = null

    /** Explicit caster family and fragment coverage selected during resolution. */
    val depthRenderKey: DepthRenderKey? get() = null

    /** Alpha threshold consumed by a masked depth/shadow fragment shader. */
    val alphaCutoff: Float get() = 0.5f

    /** Bound at [BindingSemantic.Material] -- a Vulkan descriptor set or a WebGPU bind group,
     * already written with this draw's uniforms by whoever prepared it. */
    val materialBinding: MaterialBinding

    /** Bound at vertex binding 0. Null for a draw whose vertices are generated in the shader. */
    val vertexBuffer: BufferHandle?

    /** Null for a non-indexed draw -- [elementCount] is then a vertex count. */
    val indexBuffer: BufferHandle?

    /** Index count when [indexBuffer] is set, vertex count otherwise. */
    val elementCount: Int

    /** Alpha-blended and non-depth-writing, from `RenderDrawCommand.transparent`. Held out of pipeline
     * grouping, since grouping reorders and back-to-front blending cannot survive that. */
    val transparent: Boolean get() = false

    /** Squared distance from this draw to the camera eye. Squared because the sort needs
     * ordering, not distance, and a square root per draw per frame buys nothing. Only read for a
     * [transparent] draw. */
    val depthSortKey: Float get() = 0f

    /** Clusters draws WITHIN one pipeline group so consecutive calls reuse vertex and index
     * buffer bindings -- mesh identity, in practice. Zero (the default) leaves a backend's draws
     * in submission order. */
    val batchKey: Int get() = 0

    /** Always >= 1: a plain draw is one instance, not zero. */
    val instances: Int get() = 1

    /** Per-instance model matrices, bound at vertex binding 1. */
    val instanceVertexBuffer: BufferHandle? get() = null

    /** Per-instance joint palettes, bound at [BindingSemantic.JointPalette] -- skinned
     * instancing only. */
    val jointPaletteBinding: MaterialBinding? get() = null

    /** The shadow map + its sampler, when this draw's pipeline samples one. Bound at the same
     * semantic as [jointPaletteBinding] and mutually exclusive with it: a skinned-instanced pipeline
     * reads a joint palette there, a shadowed one reads the depth target, and no pipeline
     * declares both. Vulkan leaves this null -- it binds its depth set once per pass rather
     * than per draw; WebGPU has no pass-scoped bind groups
     * outside a recorded pass, so it carries the binding per draw instead. */
    val shadowBinding: MaterialBinding? get() = null

    /** The camera-space depth target + its sampler, when this draw's pipeline samples one.
     * Its own semantic rather than [shadowBinding]'s, because a draw can read both. Null on
     * Vulkan for the same reason [shadowBinding] is. */
    val sceneDepthBinding: MaterialBinding? get() = null

    /** Per-instance color/alpha, bound at vertex binding 2 -- particles only. */
    val instanceColorBuffer: BufferHandle? get() = null

    /** Per-instance sprite frame, bound at vertex binding 3 -- particles only. */
    val instanceFrameBuffer: BufferHandle? get() = null
}
