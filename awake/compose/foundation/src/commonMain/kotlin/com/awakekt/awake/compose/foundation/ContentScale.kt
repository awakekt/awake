/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

/** How an image's pixels map into the space its node was given. */
enum class ContentScale {
    /** Scales uniformly so the whole image fits, leaving empty bands on one axis. */
    Fit,

    /** Scales uniformly so the image covers the node, cropping the overflow. */
    Crop,

    /** Stretches each axis independently to fill the node exactly. */
    FillBounds,

    /** Like [Fit], but never enlarges an image smaller than the node. */
    Inside,
}
