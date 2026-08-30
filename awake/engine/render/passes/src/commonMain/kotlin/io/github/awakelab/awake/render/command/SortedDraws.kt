/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.command

/**
 * This frame's draws, split by how they must be ordered.
 *
 * @param P The backend's own prepared-draw type.
 * @property opaqueByPipeline Batched by pipeline, then clustered by [PreparedDraw.batchKey]
 * within each group so consecutive draws reuse buffer bindings.
 * @property transparent Back to front, never grouped.
 */
class SortedDraws<P : PreparedDraw>(
    val opaqueByPipeline: Map<PipelineHandle, List<P>>,
    val transparent: List<P>,
)

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
    return SortedDraws(
        opaqueByPipeline = opaque
            .groupBy { it.pipeline }
            .mapValues { (_, group) -> group.sortedBy { it.batchKey } },
        // Descending, so the comparator is negated rather than the list reversed -- reversed()
        // would allocate a second list every frame.
        transparent = transparent.sortedByDescending { it.depthSortKey },
    )
}
