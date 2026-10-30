/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.font

import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFontSamplingMode

/**
 * Backend-neutral representation of the numeric font sampling parameters needed by a glyph shader.
 * Does not carry textures, GPU handles, or shader source.
 *
 * @property mode Sampling algorithm mode (e.g. Bitmap or Signed Distance Field).
 * @property distanceFieldRangePx Distance field search range in pixels for SDF edge transitions.
 * @property atlasWidth Width of the backing font glyph texture atlas in pixels.
 * @property atlasHeight Height of the backing font glyph texture atlas in pixels.
 * @property linearCoverage Whether coverage alpha is exact pixel area, shaped by the text coverage rule (see
 *   [UiFont.linearCoverage]).
 */
data class UiFontSamplingInfo(
    /** Sampling algorithm mode (e.g. Bitmap or Signed Distance Field). */
    val mode: UiFontSamplingMode,
    /** Distance field search range in pixels for SDF edge transitions. */
    val distanceFieldRangePx: Float = 0f,
    /** Width of the backing font glyph texture atlas in pixels. */
    val atlasWidth: Int,
    /** Height of the backing font glyph texture atlas in pixels. */
    val atlasHeight: Int,
    /** Whether coverage alpha is exact pixel area, shaped by the text coverage rule. */
    val linearCoverage: Boolean = false,
    /** The spread in atlas texels of the font's effect field, which outlines and shadows are drawn from; 0 for none. */
    val effectFieldRangePx: Float = 0f,
) {
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        mode: UiFontSamplingMode,
        distanceFieldRangePx: Float = 0f,
        atlasWidth: Int,
        atlasHeight: Int,
        linearCoverage: Boolean = false,
    ) : this(mode, distanceFieldRangePx, atlasWidth, atlasHeight, linearCoverage, 0f)

    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        mode: UiFontSamplingMode = this.mode,
        distanceFieldRangePx: Float = this.distanceFieldRangePx,
        atlasWidth: Int = this.atlasWidth,
        atlasHeight: Int = this.atlasHeight,
        linearCoverage: Boolean = this.linearCoverage,
    ): UiFontSamplingInfo = UiFontSamplingInfo(mode, distanceFieldRangePx, atlasWidth, atlasHeight, linearCoverage, effectFieldRangePx)
}

/**
 * Derives backend-neutral [UiFontSamplingInfo] from a [UiFont].
 */
val UiFont.samplingInfo: UiFontSamplingInfo
    get() = UiFontSamplingInfo(
        mode = samplingMode,
        distanceFieldRangePx = distanceFieldRangePx,
        atlasWidth = atlasWidth,
        atlasHeight = atlasHeight,
        linearCoverage = linearCoverage,
        effectFieldRangePx = effectFieldRangePx,
    )
