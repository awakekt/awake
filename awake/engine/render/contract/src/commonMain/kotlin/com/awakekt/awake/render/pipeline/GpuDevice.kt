/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset

/**
 * Awake's render hardware interface: what a GPU backend can *do*, with no knowledge of what a
 * scene *is*.
 *
 * See `docs/reference/render-hardware-interface.md` for the boundary, the naming rationale and
 * the capability-tier rule; `docs/reference/decision-log.md` D26 for why two hand-written
 * backends are kept rather than collapsing onto one.
 *
 * ## What belongs here
 *
 * Hardware vocabulary only -- resource creation, pipelines, vertex layouts, command recording,
 * device lifetime. A `GpuDevice` must never mention `RenderDrawCommand`, `SceneLight`, `Lens`, fog or a
 * clear colour: those are render *runtime* concepts, describing what to draw rather than what
 * the hardware can do.
 *
 * The acceptance test for anything added here: **could a third backend implement this without
 * changing shared code?** If not, it belongs below the line, inside a backend.
 *
 * ## Capability tiers
 *
 * This interface is the CORE tier -- the intersection of every backend, which is WebGPU-shaped
 * by arithmetic rather than preference (WebGPU is a capability subset of Vulkan, so a
 * WebGPU-shaped core is always implementable on Vulkan while the reverse never is).
 *
 * Vulkan-only capability -- explicit barriers, subpasses, bindless descriptors, multi-queue --
 * does NOT get flattened into this interface and does NOT get dropped. It goes behind an
 * optional capability interface that engine code feature-detects and never requires, so a
 * browser falls back or does without. Vulkan is the primary backend and is never held back for
 * parity; the rule is only that the core may never *require* what WebGPU cannot do.
 *
 * ## Status
 *
 * `GpuDevice` remains the resource/lifetime tier. Generic draw preparation is exposed separately
 * through `GpuDrawPreparer`, and submission consumes only `GpuPassInput` with resolved handles.
 * This keeps backend resource lookup available without putting scene vocabulary or authored
 * packet types on the renderer submission interface.
 */
interface GpuDevice {
    /** Which clip-space convention this backend's projection matrices must target -- the one
     * genuinely unavoidable difference between the two APIs' coordinate systems. */
    val clipSpace: ClipSpace

    /** Uploads [geometry] as a GPU mesh. The caller owns the result and destroys it. */
    fun createMesh(geometry: MeshGeometry): Mesh

    /**
     * Builds a [Material] -- the per-object uniform buffer plus its bound textures.
     *
     * [texture] and [renderTarget] are mutually exclusive. [uniformFloatCount] is the uniform
     * buffer's size in floats; prefer `UniformLayout.total` over a literal so the size stays
     * derived from the shader's declared block. See `Renderer.createMaterial` for the full
     * parameter contract, which this does not change.
     */
    fun createMaterial(
        texture: TextureAsset? = null,
        renderTarget: RenderTarget? = null,
        uniformFloatCount: Int = DEFAULT_UNIFORM_FLOAT_COUNT,
        pbrTextures: PbrTextureSet? = null,
    ): Material

    /** Creates an offscreen [width]x[height] colour+depth destination. Tracked by this device
     * for teardown. */
    fun createRenderTarget(width: Int, height: Int): RenderTarget

    /** Blocks until the GPU has finished all submitted work. Defaulted to a no-op: a backend
     * with no explicit synchronisation to expose, and every test double, needs nothing here. */
    fun waitIdle() {}

    /**
     * Queries an optional hardware capability of this device. Returns `null` if the capability
     * is not supported by the underlying GPU hardware or backend implementation.
     */
    fun <T : GpuCapability> capability(kind: GpuCapabilityKind<T>): T? = null

    /** Releases every GPU resource this device owns. */
    fun destroy()

    companion object {
        /** Default size in floats for standard lit material uniform buffer (MVP 16 + Light 8 = 24). */
        val DEFAULT_UNIFORM_FLOAT_COUNT: Int = UniformFields.DefaultMaterial.total
    }
}
