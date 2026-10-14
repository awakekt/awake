/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts

/** The font an element's [name] draws in, from the loaded [fonts] or the built-in families; null for the default. */
internal fun canvasFont(name: String, fonts: Map<String, UiFont>): UiFont? =
    if (isCanvasFontFile(name)) fonts[name] else BUILT_IN_FONTS[name]?.invoke()

/** What is wrong with a `font` value, as a message naming the field; null when nothing is. */
fun canvasFontProblem(font: String): String? = when {
    font.isEmpty() || isCanvasFontFile(font) || font in BUILT_IN_FONTS -> null
    '/' in font || '.' in font -> "font \"$font\" must be a .ttf or .otf file"
    else -> "font \"$font\" is not a built-in family: ${BUILT_IN_FONTS.keys.joinToString()}"
}

/** Whether [font] names a project font file by its extension, rather than a built-in family. */
fun isCanvasFontFile(font: String): Boolean = font.endsWith(".ttf", ignoreCase = true) || font.endsWith(".otf", ignoreCase = true)

/**
 * The families a `font` can name, each [UiFonts]' memoized instance, so its atlas uploads once.
 * `trueSans` is left out because an element has no weight, so it would draw as `default` does, and
 * `msdf` because nothing draws with it yet.
 */
private val BUILT_IN_FONTS: Map<String, () -> UiFont> = mapOf(
    "default" to { UiFonts.default() },
    "bitmap" to { UiFonts.bitmap() },
)
