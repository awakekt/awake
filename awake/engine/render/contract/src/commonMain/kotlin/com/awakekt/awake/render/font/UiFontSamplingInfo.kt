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
)

/**
 * Derives backend-neutral [UiFontSamplingInfo] from a [UiFont].
 */
val UiFont.samplingInfo: UiFontSamplingInfo
    get() = UiFontSamplingInfo(
        mode = samplingMode,
        distanceFieldRangePx = distanceFieldRangePx,
        atlasWidth = atlasWidth,
        atlasHeight = atlasHeight,
    )
