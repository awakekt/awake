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
import com.awakekt.awake.ui.canvas.isCanvasFontFile
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

private fun SceneNode.canvasFontPaths(): List<String> =
    components.filterIsInstance<SceneCanvasElement>().map { it.font }.filter(::isCanvasFontFile) + children.flatMap { it.canvasFontPaths() }
