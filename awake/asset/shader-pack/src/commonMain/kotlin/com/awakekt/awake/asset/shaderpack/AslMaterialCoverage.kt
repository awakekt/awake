/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslFragmentBuilder
import com.awakekt.awake.asset.shaderdsl.and
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.eq
import com.awakekt.awake.asset.shaderdsl.fwidth
import com.awakekt.awake.asset.shaderdsl.ge
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.lt
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.unaryMinus
import com.awakekt.awake.render.passes.uniforms.SceneCoverage

/**
 * Alpha for the scene pass, sharing [SceneCoverage]'s cutoff encoding with the packer. Derivatives
 * run before the hard-discard path, outside divergent control flow. A masked MSAA edge spans one
 * pixel in alpha space; hardware turns that alpha into per-sample coverage. An opaque draw stays
 * fully covered and an ordinary/transparent draw retains its original alpha and hard cutoff.
 */
internal fun AslFragmentBuilder.materialCoverage(alpha: AslExpr, cutoff: AslExpr, originalAlpha: AslExpr = alpha): AslExpr {
    val width = let("coverageWidth", max(fwidth(alpha), 0.0001f.lit))
    val threshold = -cutoff - SceneCoverage.Offset.lit
    val smooth = clamp((alpha - threshold) / width + 0.5f.lit, 0f.lit, 1f.lit)
    val coverage = let("materialCoverage", select(originalAlpha, smooth, cutoff lt 0f.lit))
    val result = let("outputAlpha", select(coverage, 1f.lit, cutoff eq SceneCoverage.Opaque.lit))
    discardIf((cutoff ge 0f.lit) and (alpha lt cutoff))
    return result
}

/** Single-sample depth passes share the material block but retain its authored hard threshold. */
internal fun hardCoverageCutoff(cutoff: AslExpr): AslExpr =
    select(select(cutoff, -cutoff - SceneCoverage.Offset.lit, cutoff lt 0f.lit), 0f.lit, cutoff eq SceneCoverage.Opaque.lit)
