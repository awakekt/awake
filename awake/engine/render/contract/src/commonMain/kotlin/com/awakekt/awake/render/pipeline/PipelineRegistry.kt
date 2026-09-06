/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

/**
 * Every pipeline this app has built, compiled once per distinct [PipelineSpec].
 *
 * See `docs/tasks/2026-08-23-rhi-gpudevice-plan.md` phase 3 for the full design.
 *
 * ## Why a registry rather than engine constructor parameters
 *
 * Both engines used to predict every pipeline at launch through seven optional shader-set
 * parameters (`instancedShaderSet`, `particleShaderSet`, ...). Each one was a branch
 * `createBackendResources` had to take, and adding a render feature meant editing both engines,
 * both bootstraps and both create bodies -- the engine had to know about a capability it does
 * not use. A feature registers what it needs instead.
 *
 * ## Why compilation happens at [register], never at [get]
 *
 * The tempting shape is `getOrCreate(spec)` called from the draw loop. It does not work here:
 *
 * - [PipelineFactory.create] is `suspend`, because shader loading is IO, and a draw loop cannot
 *   suspend.
 * - Pipeline compilation costs roughly 1-50ms. The frame budget at 60Hz is 16ms, so one cache
 *   miss mid-frame is a dropped frame. This is why Unreal and Unity ship PSO precaching rather
 *   than compiling on demand.
 *
 * So [register] compiles -- at scene load, feature activation, level transition -- and [get]
 * only looks up. A miss is a defect, not a trigger: the caller logs and skips the draw, the same
 * rule the backends already follow for a mesh whose vertex format has no pipeline. Rendering
 * wrong-format vertex data through the wrong pipeline is worse than rendering nothing.
 *
 * ## Why the key is [PipelineSpec]
 *
 * [PipelineSpec] is a `data class` over vertex format, shader resource **paths**, entry points,
 * variant, cull mode and wireframe -- value-equal, so registering the same pipeline twice
 * compiles it once.
 *
 * Two keys that look reasonable and are not:
 *
 * - **`ShaderSet`.** `ShaderStages` is a plain class, not a data class, so it has identity
 *   equality. A key holding one compares shader sets by reference, and two structurally
 *   identical sets become two entries that compile the same pipeline twice, silently. Holding
 *   `String` paths is what avoids this.
 * - **`PipelineVariant` alone.** `InstancedMeshRenderer` and `InstancedSkinnedMeshRenderer` both
 *   use `PipelineVariant.Instanced` with different vertex formats and shaders; keyed on variant,
 *   the second caller receives the first's pipeline. Wrong output, not a compile error.
 *
 * Not thread-safe: registration and frame recording both happen on the render thread.
 *
 * @param P The backend's own pipeline type.
 * @param factory Turns one spec into that backend's pipeline. The only backend-specific part.
 */
class PipelineRegistry<P>(private val factory: PipelineFactory<P>) {

    private val compiled = mutableMapOf<PipelineSpec, P>()

    /** Every spec compiled so far -- for teardown and for tests asserting what was built. */
    val specs: Set<PipelineSpec> get() = compiled.keys

    /**
     * Compiles the pipelines [requests] describe, skipping any already built.
     *
     * Suspending and slow by design -- call it off the frame path. Each request fans out into
     * its fill pipeline plus whichever companions it asked for, via the same expansion
     * [buildPipelineTable] uses.
     *
     * @param requests What a feature or scene needs in order to draw.
     * @return The pipelines built, keyed the same way [buildPipelineTable] keys them, so a
     * caller can still assemble a [PipelineTable] from one registration batch.
     */
    suspend fun register(requests: List<PipelineRequest>): Map<PipelineKey, PipelineSet<P>> =
        buildPipelineTable(requests) { key, spec -> compiled.getOrPut(spec) { factory.create(key, spec) } }

    /**
     * The pipeline already compiled for [spec], or null.
     *
     * Never compiles -- see the class doc. Null means "not registered", which the caller should
     * treat as a defect worth logging, then skip the draw.
     */
    operator fun get(spec: PipelineSpec): P? = compiled[spec]

    /** Hands every compiled pipeline to [destroy] and empties the registry. */
    fun destroyAll(destroy: (P) -> Unit) {
        compiled.values.forEach(destroy)
        compiled.clear()
    }
}
