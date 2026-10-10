/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import kotlinx.serialization.Serializable

/**
 * How a [CanvasElement] looks beyond its colours, in one place: an element has one style, and every
 * property of it is optional.
 *
 * @property image An Image's picture, the frame of a Panel, Button or Text over its background, or a
 * Bar's track.
 * @property fillImage A Bar's fill, cut at its value rather than squeezed into it.
 */
@Serializable
data class CanvasStyle(
    val image: CanvasImage? = null,
    val fillImage: CanvasImage? = null,
) {
    /** What is wrong with this style, as messages naming the field; empty when nothing is. */
    internal fun problems(): List<String> = buildList {
        image?.problems()?.forEach { add("image.$it") }
        fillImage?.problems()?.forEach { add("fillImage.$it") }
    }
}
