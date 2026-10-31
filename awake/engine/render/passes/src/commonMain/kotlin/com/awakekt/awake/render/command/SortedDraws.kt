/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

/**
 * This frame's draws, split by how they must be ordered.
 *
 * @param P The backend's own prepared-draw type.
 * @property opaqueByPipeline Batched by pipeline, then clustered by [PreparedDraw.batchKey]
 * within each group so consecutive draws reuse buffer bindings.
 * @property transparent Back to front, never grouped.
 * @property overCoveringsByPipeline The opaque [PreparedDraw.overCoverings] draws, grouped as
 * [opaqueByPipeline] is, for recording after the frame's covering content features.
 */
class SortedDraws<P : PreparedDraw>(
    val opaqueByPipeline: Map<PipelineHandle, List<P>>,
    val transparent: List<P>,
    val overCoveringsByPipeline: Map<PipelineHandle, List<P>> = emptyMap(),
) {
    /** [SortedDraws] as it was before [overCoveringsByPipeline]: hidden, and kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(opaqueByPipeline: Map<PipelineHandle, List<P>>, transparent: List<P>) :
        this(opaqueByPipeline, transparent, emptyMap())
}

/**
 * Splits [draws] into the two orderings a frame needs.
 *
 * Both backends did this themselves, and had already drifted: Vulkan clustered each pipeline
 * group by mesh to reuse buffer bindings, WebGPU did not. Sharing it means WebGPU gains that
 * clustering -- a real behaviour change, not a pure move, and the reason this is worth doing
 * beyond removing the duplication.
 *
 * Opaque draws may be reordered freely because the depth test decides what wins. Transparent
 * draws may not: blending is order-dependent, so they are held out of grouping entirely and
 * sorted farthest-first so nearer surfaces blend over what is already there.
 *
 * @param P The backend's own prepared-draw type.
 * @param draws This frame's prepared draws, in submission order.
 * @return The same draws, reordered for recording.
 */
fun <P : PreparedDraw> sortForRecording(draws: List<P>): SortedDraws<P> {
    val (transparent, opaque) = draws.partition { it.transparent }
    // Almost every frame has none, so the common case skips the second partition's lists.
    val overCoverings = if (opaque.any { it.overCoverings }) opaque.filter { it.overCoverings } else emptyList()
    val covered = if (overCoverings.isEmpty()) opaque else opaque.filterNot { it.overCoverings }
    return SortedDraws(
        opaqueByPipeline = covered.groupedForRecording(),
        transparent = transparent.sortedWith(TransparentOrder),
        overCoveringsByPipeline = overCoverings.groupedForRecording(),
    )
}

private fun <P : PreparedDraw> List<P>.groupedForRecording(): Map<PipelineHandle, List<P>> =
    groupBy { it.pipeline }.mapValues { (_, group) -> group.sortedBy { it.batchKey } }

/**
 * Paint layer, then paint order within it, then farthest first. Depth is descending in the comparator
 * rather than the list reversed, which would allocate a second list every frame.
 */
private val TransparentOrder: Comparator<PreparedDraw> =
    compareBy<PreparedDraw> { it.sortLayer }.thenBy { it.sortOrder }.thenByDescending { it.depthSortKey }

/** Flattened list of all prepared draws in this sorted set. */
val <P : PreparedDraw> SortedDraws<P>.allDraws: List<P>
    get() = opaqueByPipeline.values.flatten() + overCoveringsByPipeline.values.flatten() + transparent
