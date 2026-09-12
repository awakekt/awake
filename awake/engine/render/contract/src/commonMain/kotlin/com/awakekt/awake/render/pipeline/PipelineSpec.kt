/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.renderer.UniformLayout

/**
 * Identifies ONE pipeline family a [PipelineRequest] builds, and the key it lands under in
 * [buildPipelineTable]'s result.
 *
 * [Primary] is a distinct case from [Format] on purpose: the primary pipeline's own
 * [VertexFormat] can numerically equal one an additional-format request also uses (nothing stops
 * a caller targeting the same format twice), but the two are never the same sealed case, so they
 * can never collide as map keys.
 */
sealed interface PipelineKey {
    data object Primary : PipelineKey
    data class Format(val vertexFormat: VertexFormat) : PipelineKey
    data object Instanced : PipelineKey
    data object SkinnedInstanced : PipelineKey
    data object Particle : PipelineKey

    /**
     * One app-supplied content feature's pipeline, keyed by its own name.
     *
     * A data class rather than more objects because the engine does not know what content
     * exists -- that is the whole point of a content feature. Two features with the same name
     * collide, which is the correct failure: they would also collide in every log line.
     */
    data class Content(val name: String) : PipelineKey
}

/**
 * ONE pipeline for a backend's [PipelineFactory] to build -- everything about a pipeline that
 * differs between backends' *spellings* but not between their *meanings*.
 *
 * Deliberately carries no backend type. [wireframe] is a boolean rather than a polygon mode
 * because the two backends express it differently and neither spelling belongs in shared code:
 * Vulkan uses `VK_POLYGON_MODE_LINE`, WebGPU a `LineList` topology. Same for [cullMode], which
 * reuses the render contract's own [CullMode] rather than `VkCullModeFlagBits`/`GPUCullMode`.
 *
 * Shader *sources* rather than loaded bytes: loading is suspending and per-backend (SPIR-V pairs
 * vs. a single WGSL source), so [PipelineFactory] owns it and this stays a plain description.
 * A [ShaderSource] rather than a resource path, so a shader that carries its own WGSL --
 * anything built from an ASL definition -- can be described here at all.
 */
data class PipelineSpec(
    val vertexFormat: VertexFormat,
    val vertexShader: ShaderSource,
    val fragmentShader: ShaderSource,
    val variant: PipelineVariant = PipelineVariant.Opaque,
    val cullMode: CullMode = CullMode.None,
    val frontFace: FrontFace = FrontFace.CounterClockwise,
    val wireframe: Boolean = false,
    /** Semantic descriptor-set/bind-group layout consumed by this pipeline. */
    val bindingLayout: BindingLayout = BindingLayout.Standard,
    /** Exact resources used by each shader group, when the shader declaration provides them. */
    val bindingsByGroup: Map<Int, GroupBindings> = emptyMap(),
    /** True when [bindingsByGroup] is authoritative, including an explicitly empty layout. */
    val bindingsMetadataAvailable: Boolean = false,
    /**
     * What occupies this pipeline's material group, or null to keep the fixed glTF
     * metallic-roughness shape both backends build today.
     *
     * Null is not "no bindings" -- it is "the bindings every pipeline had before this field
     * existed", so an existing spec keeps its exact layout. A pipeline needing anything else,
     * such as a terrain splat weightmap, states it here rather than being unable to express it
     * at all. See [GroupBindings] for why [bindingLayout] alone was not enough.
     */
    val materialBindings: GroupBindings? = null,
    /** Whether this pipeline binds semantic group 0 during recording. */
    val usesMaterialGroup: Boolean = true,
    /**
     * The uniform block this pipeline owns, or null when its uniforms come from a per-draw
     * `Material` instead.
     *
     * Non-null is what a content feature needs: a sky has one block per frame that belongs to the
     * pipeline, not to any mesh's material. The factory allocates it and the pipeline it returns
     * implements `UniformBlock`. Sized from the layout rather than a caller-supplied count -- see
     * that type's own doc comment for why every hand-summed uniform size in this repo became a
     * bug.
     */
    val uniforms: UniformLayout? = null,
) {
    /** The vertex stage's entry point -- read straight off [vertexShader]. */
    val vertexEntryPoint: String get() = vertexShader.entryPoint

    /** The fragment stage's entry point -- read straight off [fragmentShader]. */
    val fragmentEntryPoint: String get() = fragmentShader.entryPoint
}

/**
 * A [PipelineSpec] plus which companion pipelines to build alongside it.
 *
 * The companions all reuse the request's own shaders and differ only in one state each, which is
 * why they're flags here rather than four near-identical specs a caller has to keep in sync. The
 * fan-out itself lives in [buildPipelineTable], once, instead of being open-coded per backend --
 * that duplication is what made adding [buildTransparent] a one-line change on one backend and a
 * three-site copy-paste on the other.
 */
data class PipelineRequest(
    val key: PipelineKey,
    val spec: PipelineSpec,
    /** A `LineList`/`POLYGON_MODE_LINE` companion. Only ever set for the primary/[PipelineKey
     * .Format] requests -- instanced/skinned/particle pipelines have never had one. */
    val buildWireframe: Boolean = false,
    /** A back-face-culled companion -- see [CullMode]'s own doc comment. Same scope as
     * [buildWireframe]; instanced and particle meshes don't opt into per-mesh culling yet. */
    val buildBackCulled: Boolean = false,
    /** An alpha-blended, non-depth-writing companion for `RenderDrawCommand.transparent` draws. Same
     * scope again, since instanced and particle draws carry their own blend variants already. */
    val buildTransparent: Boolean = false,
)

/**
 * The up-to-four pipelines one [PipelineRequest] produces. A companion is null exactly when its
 * `build*` flag was false.
 */
data class PipelineSet<P>(
    val fill: P,
    val wireframe: P? = null,
    val backCulled: P? = null,
    val transparent: P? = null,
) {
    /**
     * Every pipeline in this set, for teardown.
     *
     * Here rather than spelled out at each backend's `destroyBackend`: both of them enumerated
     * the companions by hand and both forgot [transparent] when it was added, leaking one
     * pipeline per vertex format on every backend at once. A companion added to this class from
     * now on joins this list automatically.
     */
    val all: List<P> get() = listOfNotNull(fill, wireframe, backCulled, transparent)
}

/**
 * A backend's ability to turn one [PipelineSpec] into its own pipeline object.
 *
 * The entire backend-specific half of pipeline creation. Everything else -- which pipelines
 * exist, which companions each one needs, what variant those companions get, and which key they
 * land under -- is [buildPipelineTable]'s job and is written once.
 */
fun interface PipelineFactory<P> {
    /**
     * @param key Which pipeline family [spec] belongs to. Passed alongside the spec because a
     * backend may owe one family something the shared spec deliberately doesn't model -- Vulkan's
     * skinned-instanced pipeline needs the joint-palette descriptor set layout appended, which is
     * a Vulkan concept with no WebGPU counterpart and no business in [PipelineSpec].
     * @param spec The pipeline to build, already fully decided by [buildPipelineTable].
     */
    suspend fun create(key: PipelineKey, spec: PipelineSpec): P
}
