/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads and loads every font file [document]'s `canvas_element`s name, once each, by path. A file
 * that cannot be read, or is not a font [UiFonts.load] reads, is logged once and left out, and its
 * elements draw in the default font. A built-in family needs no file and is not here.
 *
 * A font takes tens of milliseconds to load, so call this while a scene loads, not while it plays. A
 * host that draws nothing, such as a game server, has no need to call it.
 */
// A file's reader fails with an exception of its own, and a corrupt font must not fail the scene.
@Suppress("TooGenericExceptionCaught")
suspend fun loadCanvasFonts(document: SceneDocument, assets: AssetSource): Map<String, UiFont> = buildMap {
    for (path in document.nodes.flatMap { it.canvasFontPaths() }.distinct()) {
        try {
            put(path, UiFonts.load(assets.read(AssetPath(path)).getOrThrow(), path))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.warn { "Canvas font '$path' could not load, so its elements draw in the default font: ${failure.message}" }
        }
    }
}

/** The font an element's [name] draws in, from the loaded [fonts] or the built-in families; null for the default. */
internal fun canvasFont(name: String, fonts: Map<String, UiFont>): UiFont? =
    if (isFontFile(name)) fonts[name] else BUILT_IN_FONTS[name]?.invoke()

/** What is wrong with a `font` value, as a message naming the field; null when nothing is. */
internal fun fontProblem(font: String): String? = when {
    font.isEmpty() || isFontFile(font) || font in BUILT_IN_FONTS -> null
    '/' in font || '.' in font -> "font \"$font\" must be a .ttf or .otf file"
    else -> "font \"$font\" is not a built-in family: ${BUILT_IN_FONTS.keys.joinToString()}"
}

private fun isFontFile(font: String) = font.endsWith(".ttf", ignoreCase = true) || font.endsWith(".otf", ignoreCase = true)

/**
 * The families a `font` can name, each [UiFonts]' memoized instance, so its atlas uploads once.
 * `trueSans` is left out because an element has no weight, so it would draw as `default` does, and
 * `msdf` because nothing draws with it yet.
 */
private val BUILT_IN_FONTS: Map<String, () -> UiFont> = mapOf(
    "default" to { UiFonts.default() },
    "bitmap" to { UiFonts.bitmap() },
)

private fun SceneNode.canvasFontPaths(): List<String> =
    components.filterIsInstance<SceneCanvasElement>().map { it.font }.filter(::isFontFile) + children.flatMap { it.canvasFontPaths() }
