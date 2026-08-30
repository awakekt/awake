/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.font

import io.github.awakelab.awake.core.text.font.UiFont
import io.github.awakelab.awake.core.text.font.UiFontSamplingMode

/**
 * Backend-neutral representation of the numeric font sampling parameters needed by a glyph shader.
 * Does not carry textures, GPU handles, or shader source.
 */
data class UiFontSamplingInfo(
    val mode: UiFontSamplingMode,
    val distanceFieldRangePx: Float = 0f,
    val atlasWidth: Int,
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
