/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.pipeline.AlphaMode

/**
 * The scene shader's `pbrFactors.z` encoding. Nonnegative values keep the existing hard cutoff.
 * Masked MSAA values are `-(cutoff + 1)`, including cutoff endpoints; [Opaque] forces full
 * coverage for an opaque material whose texture alpha has no meaning. Single-sample depth
 * shaders decode the same block back to its hard cutoff; they never use coverage smoothing.
 */
object SceneCoverage {
    /** The offset separating a coverage cutoff from an ordinary hard cutoff. */
    const val Offset: Float = 1f

    /** Full coverage, regardless of sampled alpha. Outside the masked interval [-2, -1]. */
    const val Opaque: Float = -3f
}

/** Packs coverage only when the selected opaque pipeline enables alpha to coverage. */
fun RenderDrawCommand.sceneCoverageCutoff(alphaToCoverageEnabled: Boolean): Float = when {
    !alphaToCoverageEnabled || transparent -> coverageCutoff
    alphaMode == AlphaMode.Masked -> -(alphaCutoff.coerceIn(0f, 1f) + SceneCoverage.Offset)
    else -> SceneCoverage.Opaque
}
