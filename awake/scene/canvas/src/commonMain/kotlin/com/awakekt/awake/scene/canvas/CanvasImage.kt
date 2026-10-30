/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.decodeImageBitmap
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.ui.canvas.CanvasStyle
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads and decodes every image [document]'s `canvas_element`s name, once each, by path: each
 * style's `image` and `fillImage`, and the `image` of its `hovered` and `pressed` states. An image
 * that cannot be read or decoded is logged and left out, and its elements draw without it.
 */
// A file's reader and each platform's decoder fail with exceptions of their own.
@Suppress("TooGenericExceptionCaught")
suspend fun loadCanvasImages(document: SceneDocument, assets: AssetSource): Map<String, ImageBitmap> = buildMap {
    for (path in document.canvasImagePaths()) {
        try {
            put(path, decodeImageBitmap(assets.read(AssetPath(path)).getOrThrow()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.warn { "Canvas image '$path' could not load: ${failure.message}" }
        }
    }
}

/**
 * Reads every image [document]'s `canvas_element`s name, once each, as [loadCanvasImages] does, but
 * decodes none: for a host that draws nothing, such as a game server, whose pictures would only fill
 * memory. A sheet of 2048 by 1856 pixels takes 15 MB decoded. An image that cannot be read is logged
 * the same way.
 *
 * @return The paths that could not be read, in the order [document] names them.
 */
// A file's reader fails with an exception of its own.
@Suppress("TooGenericExceptionCaught")
suspend fun checkCanvasImages(document: SceneDocument, assets: AssetSource): List<String> = buildList {
    for (path in document.canvasImagePaths()) {
        try {
            assets.read(AssetPath(path)).getOrThrow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.warn { "Canvas image '$path' could not load: ${failure.message}" }
            add(path)
        }
    }
}

/** Whether a node of [document], at any depth, has a `canvas_element` that draws an image. */
fun hasCanvasImages(document: SceneDocument): Boolean = document.nodes.any { it.canvasImagePaths().isNotEmpty() }

private fun SceneDocument.canvasImagePaths(): List<String> = nodes.flatMap { it.canvasImagePaths() }.distinct()

private fun SceneNode.canvasImagePaths(): List<String> =
    components.filterIsInstance<SceneCanvasElement>().flatMap { it.style.imagePaths() } +
        components.filterIsInstance<SceneCanvasParticles>().mapNotNull { it.texture.takeIf(String::isNotEmpty) } +
        children.flatMap { it.canvasImagePaths() }

/** Every image this style draws: its picture or frame, a Bar's fill, and a Button's hovered and pressed pictures. */
private fun CanvasStyle.imagePaths(): List<String> = listOfNotNull(image?.path, fillImage?.path, hovered?.image?.path, pressed?.image?.path)

internal val log = Logger("scene-canvas")
