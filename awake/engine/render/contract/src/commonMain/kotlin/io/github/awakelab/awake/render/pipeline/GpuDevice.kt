/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.pipeline

import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.math.ClipSpace
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh
import io.github.awakelab.awake.render.texture.PbrTextureSet
import io.github.awakelab.awake.render.texture.RenderTarget
import io.github.awakelab.awake.render.texture.TextureAsset

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
 * device lifetime. A `GpuDevice` must never mention `DrawCall`, `SceneLight`, `Lens`, fog or a
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
 * Phase 1 of the migration: this aggregates members `Renderer` already declared, so every
 * backend satisfies it today without changing a line. `Renderer` extends it, which is what makes
 * the boundary real without moving code.
 *
 * Still to come, and deliberately absent for now: command recording (`CommandRecorder` exists but
 * is not yet reachable from here) and the capability accessor above. Both arrive with the
 * draw-preparation phase, which is also what removes the backends' current imports of `DrawCall`,
 * `SceneLight` and `Lens` -- that phase is finished when `grep -rl DrawCall awake/backend/`
 * returns nothing.
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

    /** Releases every GPU resource this device owns. */
    fun destroy()

    companion object {
        /** MVP (16) + a `SceneLight`'s direction and colour as two `vec4f` (8). The default
         * every material that goes through the standard lit pass needs. */
        const val DEFAULT_UNIFORM_FLOAT_COUNT = 24
    }
}
