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
import com.awakekt.awake.render.renderer.GpuResourceStats
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

    /** Coarse hardware classification used to choose a default [EffectQuality] tier. */
    val deviceClass: GpuDeviceClass
        get() = GpuDeviceClass.Unknown

    /**
     * The GPU's name as its driver reports it, such as `Mali-G78`, `Adreno (TM) 660` or
     * `llvmpipe (LLVM 17.0.6, 256 bits)`: for a quality tier [deviceClass] is too coarse to pick (a
     * low-end and a high-end phone GPU are both [GpuDeviceClass.Mobile]), and to name the GPU in a
     * performance report. Null where the platform gives no name: a browser may hide it, or report
     * only its vendor (#1700).
     */
    val deviceName: String?
        get() = null

    /**
     * The GPU vendor's PCI id, such as `0x10DE` (NVIDIA), `0x1002` (AMD), `0x8086` (Intel), `0x13B5`
     * (Arm) or `0x5143` (Qualcomm). Null where the platform reports none, as WebGPU, which names the
     * vendor instead of numbering it (#1700).
     */
    val vendorId: Int?
        get() = null

    /**
     * The GPU driver's version, encoded as its vendor encodes it: comparable between two readings
     * from the same vendor, not across vendors. Null where the platform reports none (#1700).
     */
    val driverVersion: Long?
        get() = null

    /** Uploads [geometry] as a GPU mesh. The caller owns the result and destroys it. */
    fun createMesh(geometry: MeshGeometry): Mesh

    /**
     * Uploads only [geometry]'s indices, as a mesh that draws [vertices]' own vertex buffer: meshes
     * that differ only in their triangles, such as one model's levels of detail, then hold one copy
     * of their vertices between them. [geometry]'s vertices and format are [vertices]'.
     *
     * The caller owns the result and destroys it, in either order with [vertices]: the shared buffer
     * lives until the last mesh drawing it is destroyed. A backend that cannot share, or a
     * [vertices] it did not create or has destroyed, uploads [geometry] whole, which is the default.
     */
    fun createMeshSharingVertices(vertices: Mesh, geometry: MeshGeometry): Mesh = createMesh(geometry)

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
     * Releases reclaimable temporary GPU caches and pools (staging buffers, retired resources,
     * idle transient allocations) without destroying caller-owned meshes, materials, or textures.
     *
     * Safe to invoke between frames during memory pressure, scene transitions, or backgrounding.
     * Backends without explicit pool reclamation leave this a no-op.
     */
    fun trimMemory() {}

    /**
     * Queries an optional hardware capability of this device. Returns `null` if the capability
     * is not supported by the underlying GPU hardware or backend implementation.
     */
    fun <T : GpuCapability> capability(kind: GpuCapabilityKind<T>): T? = null

    /**
     * Snapshot of live GPU resources currently held or tracked by this device, or `null` when a
     * backend does not report resource accounting. Intended for leak assertions and runtime
     * memory diagnostics; reading it is pull-only and never runs automatically per frame.
     */
    val gpuResourceStats: GpuResourceStats?
        get() = null

    /** Releases every GPU resource this device owns. */
    fun destroy()

    /** Constants and defaults for [GpuDevice] buffer allocations. */
    companion object {
        /** Default size in floats for standard lit material uniform buffer (MVP 16 + Light 8 = 24). */
        val DEFAULT_UNIFORM_FLOAT_COUNT: Int = UniformFields.DefaultMaterial.total
    }
}
