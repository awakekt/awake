/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("MatchingDeclarationName")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.skin.BarKey
import com.awakekt.awake.compose.foundation.skin.FrameKey
import com.awakekt.awake.compose.foundation.skin.PartKey
import com.awakekt.awake.compose.foundation.skin.Skin
import com.awakekt.awake.compose.foundation.skin.StatesKey
import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color

/**
 * The parts of a [Skin][com.awakekt.awake.compose.foundation.skin.Skin] the shadcn components read.
 * When `LocalSkin` provides a part, the component draws it in place of its token look; without the
 * skin or the part the look is unchanged. Check a skin with `skin.requireParts(ShadcnSkinKeys.All)`.
 */
object ShadcnSkinKeys {
    /** [ShadcnButton]'s four-state face: normal, hovered, pressed, disabled. */
    val ButtonFace = StatesKey("shadcn.button.face")

    /** [ShadcnCard]'s nine-slice frame. Content is inset by the larger slice inset of each axis. */
    val CardFrame = FrameKey("shadcn.card.frame")

    /** [ShadcnProgressBar]'s track, used when the caller passes no `track`. */
    val ProgressTrack = FrameKey("shadcn.progress.track")

    /** [ShadcnProgressBar]'s fill, used when the caller passes no `fill`; end caps stay whole. */
    val ProgressFill = BarKey("shadcn.progress.fill")

    /** Every key above. */
    val All: List<PartKey<*>> = listOf(ButtonFace, CardFrame, ProgressTrack, ProgressFill)
}

/** Draws nothing of its own: the token background and border give way to a skin's art, and a disabled button is not dimmed. */
internal val SkinFaceStyle = Style {
    background(Color.Transparent)
    border(0.dp, Color.Transparent)
    alpha(1f)
}

/** The content inset of a skin's frame [key] on one axis, in dp: the larger of its two slice insets. */
internal fun Skin.insetDp(key: FrameKey, vertical: Boolean): Dp {
    val insets = sliceInsets(key) ?: return 0.dp
    val pixels = if (vertical) maxOf(insets.top, insets.bottom) else maxOf(insets.left, insets.right)
    return (pixels / pixelsPerDp).dp
}
