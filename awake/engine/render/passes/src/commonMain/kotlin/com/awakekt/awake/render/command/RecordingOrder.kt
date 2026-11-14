/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

/**
 * This input's draws grouped for recording: as they are when the compiler already put them in
 * recording order ([GpuPassInput.drawsInRecordingOrder]), and sorted with [sortForRecording] otherwise.
 *
 * The compiler sorts a frame's draws once; an executor sorting them again rebuilt the same order and
 * allocated the concatenated list, the partition, the per-pipeline lists and their arrays every
 * frame, a reused frame included (#1555). In order, the opaque draws come in one run per pipeline,
 * so each pipeline's draws are a view of the list, not a copy. Should a pipeline's draws ever come
 * in two runs, the draws are sorted as before rather than one run lost.
 */
fun GpuPassInput.drawsForRecording(): SortedDraws<GpuResolvedDraw> =
    (if (drawsInRecordingOrder) inRecordingOrder() else null) ?: sortForRecording(resolvedDraws)

/** The opaque draws' runs by pipeline, or null when a pipeline has two runs. */
private fun GpuPassInput.inRecordingOrder(): SortedDraws<GpuResolvedDraw>? {
    val opaque = resolvedOpaqueDraws
    val byPipeline = LinkedHashMap<PipelineHandle, List<GpuResolvedDraw>>()
    var start = 0
    var oneRunEach = true
    while (oneRunEach && start < opaque.size) {
        val pipeline = opaque[start].pipeline
        var end = start + 1
        while (end < opaque.size && opaque[end].pipeline == pipeline) end++
        oneRunEach = byPipeline.put(pipeline, opaque.subList(start, end)) == null
        start = end
    }
    return if (oneRunEach) SortedDraws(byPipeline, resolvedTransparentDraws) else null
}
