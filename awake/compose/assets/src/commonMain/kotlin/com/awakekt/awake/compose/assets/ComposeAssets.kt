/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.assets

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocal
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger

/**
 * The project's files the UI below reads from, or null where nothing provides them. A played
 * project's UI host provides its files to the scene's Compose UI and canvas, so they need no setup.
 */
val LocalAssetSource: CompositionLocal<AssetSource?> = compositionLocalOf { null }

/**
 * The image cache [rememberAssetImage] reads through, or null where nothing provides one. A played
 * project's UI host provides one per scene, started with the scene's canvas images and freed when
 * the scene is left.
 */
val LocalAssetImages: CompositionLocal<AssetImages?> = compositionLocalOf { null }

context(composer: Composer)
/**
 * Provides [source], and [images] when given, to [content]: for a host other than a played
 * project's, such as an editor panel or a test, which owns both and their lifetime.
 *
 * @param source The files [LocalAssetSource] gives.
 * @param images The cache [rememberAssetImage] reads through, usually over the same [source].
 * @param content The UI that reads them.
 */
fun ProvideAssets(
    source: AssetSource,
    images: AssetImages? = null,
    content: context(Composer) () -> Unit,
) {
    if (images == null) {
        CompositionLocalProvider(LocalAssetSource, source, content)
    } else {
        CompositionLocalProvider(LocalAssetSource provides source, LocalAssetImages provides images, content = content)
    }
}

context(composer: Composer)
/**
 * The image at the asset [path], or null until it has loaded: `rememberAssetImage("ui/frame.png")`.
 *
 * Read through [LocalAssetImages], so the file is read and decoded once per scene, off the frame,
 * and an image a scene's canvas elements already name is the one they draw. Ask every frame: the
 * first ask starts the load, and once loaded an ask allocates nothing.
 *
 * It never throws in the frame. A path that cannot be read or decoded stays null and is logged once;
 * [AssetImages.failure] says why. With no [LocalAssetImages] it is null too, logged once per path.
 *
 * ```kotlin
 * val frame = rememberAssetImage("ui/window-frame.png")
 * if (frame != null) Image(frame, contentDescription = null, modifier = Modifier.size(240.dp, 160.dp))
 * ```
 *
 * @param path The asset path, root-relative, as a scene names its images.
 * @return The decoded image, or null.
 */
fun rememberAssetImage(path: String): ImageBitmap? {
    val images = LocalAssetImages.current ?: return missingImages(path)
    return images[path]
}

private val log = Logger("compose-assets")
private val pathsWithoutImages = HashSet<String>()

private fun missingImages(path: String): ImageBitmap? {
    if (pathsWithoutImages.add(path)) log.warn { "No LocalAssetImages for '$path'; it shows nothing. Provide one with ProvideAssets." }
    return null
}
