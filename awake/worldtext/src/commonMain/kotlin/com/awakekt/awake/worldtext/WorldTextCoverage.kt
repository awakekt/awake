/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.worldtext

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How far, in field units, the distance field changes across one screen pixel: the edge width
 * [worldTextCoverage] ramps over. [gradientX] and [gradientY] are the screen-space derivatives of the
 * field's true signed distance; [texelsAcross] and [texelsDown] how many atlas texels one pixel spans
 * along the screen's two axes; [rangeTexels] the field's spread. The measured gradient is kept within
 * what that footprint allows, and at most one field unit, past which the mip-filtered field is the
 * pixel's coverage.
 *
 * The CPU copy of `edgeWidth` in `WorldTextShader`, held to it by `WorldTextShaderTwinTest`.
 */
fun worldTextEdgeWidth(gradientX: Float, gradientY: Float, texelsAcross: Float, texelsDown: Float, rangeTexels: Float): Float {
    val narrowest = min(texelsAcross, texelsDown) / rangeTexels
    val widest = max(texelsAcross, texelsDown) / rangeTexels
    val measured = sqrt(gradientX * gradientX + gradientY * gradientY).coerceIn(narrowest, widest)
    return min(measured, 1f).coerceIn(MIN_EDGE_WIDTH, 1f)
}

/**
 * The coverage world text draws a pixel with: the median of the atlas sample's three distance
 * channels ([r], [g], [b]) through a ramp [edgeWidth] wide, centred on the field's 0.5 edge.
 * [edgeWidth] is how far the field's true distance changes across one screen pixel, which the shader
 * takes from screen-space derivatives, so the ramp is one pixel wide at any distance and angle.
 *
 * The CPU copy of `WorldTextShader`'s fragment (`awake:asset:shader-pack`), held to it by
 * `WorldTextShaderTwinTest`. A host with no GPU, or a test, works out what a pixel draws with it.
 */
fun worldTextCoverage(r: Float, g: Float, b: Float, edgeWidth: Float): Float {
    val signedDistance = max(min(r, g), min(max(r, g), b))
    return ((signedDistance - EDGE) / max(edgeWidth, MIN_EDGE_WIDTH) + EDGE).coerceIn(0f, 1f)
}

private const val EDGE = 0.5f

/** The shader's narrowest edge, in field units a pixel. */
private const val MIN_EDGE_WIDTH = 1e-4f
