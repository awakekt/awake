/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.debug

import com.awakekt.awake.render.passes.RenderDrawCommand

/**
 * The blended draws one view of the scene submitted, by where they came from (#1621): the mesh
 * renderers its culling kept, the particle emitters, and each render feature by name. A view that
 * draws more transparent geometry than expected reads here which source to look at.
 *
 * Counts are of submitted draws, before the compiler folds copies: blended draws do not fold, so
 * they are also what the transparent pass draws, less any the backend cannot resolve.
 *
 * Filled while [RenderDiagnostics.drawStatsEnabled] is on, one instance per view reused frame after
 * frame; see [RenderDiagnostics.transparentSources] and [RenderDiagnostics.captureTransparentSources].
 */
class TransparentDrawSources internal constructor(
    /** Each render feature's name, in the order the scene registered them. */
    val featureNames: List<String>,
) {
    private val featureCounts = IntArray(featureNames.size)

    /** Blended draws from mesh renderers, after this view's culling. */
    var meshRenderers: Int = 0
        private set

    /** Blended draws from particle emitters. */
    var particles: Int = 0
        private set

    /** All of this view's blended draws. */
    val total: Int get() = meshRenderers + particles + featureCounts.sum()

    /** The blended draws the feature at [index] in [featureNames] submitted. */
    fun featureDraws(index: Int): Int = featureCounts[index]

    /** The blended draws the feature named [name] submitted; 0 for a name it has not. */
    fun featureDraws(name: String): Int = featureNames.indexOf(name).let { if (it < 0) 0 else featureCounts[it] }

    override fun toString(): String = buildString {
        append("transparent ").append(total).append(": mesh renderers ").append(meshRenderers)
        append(", particles ").append(particles)
        featureNames.forEachIndexed { index, name -> append(", ").append(name).append(' ').append(featureCounts[index]) }
    }

    internal fun reset() {
        meshRenderers = 0
        particles = 0
        featureCounts.fill(0)
    }

    internal fun addMeshRenderers(draws: List<RenderDrawCommand>) {
        meshRenderers += blended(draws)
    }

    internal fun addParticles(draws: List<RenderDrawCommand>) {
        particles += blended(draws)
    }

    internal fun addFeature(index: Int, draws: List<RenderDrawCommand>) {
        featureCounts[index] += blended(draws)
    }

    private fun blended(draws: List<RenderDrawCommand>): Int {
        var count = 0
        for (index in draws.indices) if (draws[index].transparent) count++
        return count
    }
}
