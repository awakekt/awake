/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

/**
 * How an image is sampled when drawn larger or smaller than its pixels, as Jetpack Compose's
 * `FilterQuality` names it.
 */
enum class FilterQuality {
    /** The nearest texel, so pixel art stays crisp at any whole-number scale. */
    None,

    /** Neighbouring texels blended, so a photo or painted image scales smoothly. */
    Low,
}
