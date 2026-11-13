/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.skin

/**
 * The part keys the skinned components read. A skin declares the ones it supplies; check a skin
 * against what an app uses with `skin.requireParts(SkinnedKeys.All)`. A skin that lacks a key is
 * still valid: that component keeps its token look.
 */
object SkinnedKeys {
    /** The four-state face of [SkinnedButton]. */
    val ButtonFace = StatesKey("skinned.button.face")

    /** The nine-slice frame of [SkinnedFrame]. */
    val Frame = FrameKey("skinned.frame")

    /** The empty track behind a [SkinnedBar]. */
    val BarTrack = FrameKey("skinned.bar.track")

    /** The fill of a [SkinnedBar], drawn with its end caps whole. */
    val BarFill = BarKey("skinned.bar.fill")

    /** Every key above. */
    val All: List<PartKey<*>> = listOf(ButtonFace, Frame, BarTrack, BarFill)
}
