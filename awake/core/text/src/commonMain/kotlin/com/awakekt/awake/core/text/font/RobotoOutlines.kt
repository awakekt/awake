/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalEncodingApi::class)

package com.awakekt.awake.core.text.font

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The outlines and pair kerning of the built-in Roboto's atlas glyphs, one face per weight, read from
 * [RobotoUiFontOutlineData] the first time a face's [PackedUiFont.pixelGlyphs] or kerning are asked for.
 *
 * Shared by every [PackedUiFont] of a weight, whatever its cell size, so a face is decoded once.
 */
internal object RobotoOutlines {
    val thin: OpenTypeFont by lazy { read(RobotoUiFontOutlineData.THIN, "Roboto Thin") }
    val light: OpenTypeFont by lazy { read(RobotoUiFontOutlineData.LIGHT, "Roboto Light") }
    val regular: OpenTypeFont by lazy { read(RobotoUiFontOutlineData.REGULAR, "Roboto Regular") }
    val medium: OpenTypeFont by lazy { read(RobotoUiFontOutlineData.MEDIUM, "Roboto Medium") }
    val semiBold: OpenTypeFont by lazy { read(RobotoUiFontOutlineData.SEMI_BOLD, "Roboto SemiBold") }
    val bold: OpenTypeFont by lazy { read(RobotoUiFontOutlineData.BOLD, "Roboto Bold") }
    val black: OpenTypeFont by lazy { read(RobotoUiFontOutlineData.BLACK, "Roboto Black") }

    private fun read(base64: String, name: String): OpenTypeFont =
        OpenTypeFont(Base64.Default.decode(base64.filterNot(Char::isWhitespace)), name)
}
