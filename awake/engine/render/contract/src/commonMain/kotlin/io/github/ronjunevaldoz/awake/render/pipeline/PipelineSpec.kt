// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.pipeline

import io.github.ronjunevaldoz.awake.core.geometry.VertexFormat
import io.github.ronjunevaldoz.awake.render.renderer.CullMode

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
 * Shader *paths* rather than loaded bytes: loading is suspending and per-backend (SPIR-V pairs
 * vs. a single WGSL source), so [PipelineFactory] owns it and this stays a plain description.
 */
data class PipelineSpec(
    val vertexFormat: VertexFormat,
    val vertexShaderResourcePath: String,
    val fragmentShaderResourcePath: String,
    val vertexEntryPoint: String,
    val fragmentEntryPoint: String,
    val variant: PipelineVariant = PipelineVariant.Opaque,
    val cullMode: CullMode = CullMode.None,
    val wireframe: Boolean = false,
)

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
    /** An alpha-blended, non-depth-writing companion for `DrawCall.transparent` draws. Same
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
