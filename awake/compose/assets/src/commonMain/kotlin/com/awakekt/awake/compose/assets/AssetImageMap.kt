/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.assets

import com.awakekt.awake.compose.ui.graphics.ImageBitmap

/**
 * [images] seen as a map by path, for a UI that takes its pictures as one, such as a scene canvas:
 * looking a path up asks [images] for it, which starts its load the first time, and returns null
 * until it has decoded. A lookup inside a composition is read through the path's [AssetImage] state,
 * so the composition is drawn again once the image arrives.
 *
 * Only what has loaded is listed by [keys], [entries] and [size]. Look paths up on the frame thread.
 */
internal class AssetImageMap(private val images: AssetImages) : AbstractMap<String, ImageBitmap>() {
    // A blank path names no file: an element whose image has none draws without it, unasked.
    override fun get(key: String): ImageBitmap? = if (key.isBlank()) null else images.state(key).value

    override fun containsKey(key: String): Boolean = get(key) != null

    override val entries: Set<Map.Entry<String, ImageBitmap>>
        get() = images.loaded().entries
}
