/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "MatchingDeclarationName")

package com.awakekt.awake.compose.foundation.skin

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.FillDirection
import com.awakekt.awake.core.color.Color

/** The token look of a [SkinnedBar]: the empty [track] and the [fill]. */
class SkinnedBarColors(
    val track: Color = Color(0.08f, 0.09f, 0.12f),
    val fill: Color = Color(0.25f, 0.70f, 0.35f),
)

/**
 * A gauge: the [track] part of [LocalSkin] with the [fill] part drawn over it to [value] (0..1,
 * read each draw, so a change redraws with no recomposition). The fill's end caps stay whole at
 * every value above 0. Give it a size with [modifier]; [direction] picks horizontal or vertical.
 * With no skin or a skin lacking a part, that part is drawn from [colors].
 */
context(_: Composer)
fun SkinnedBar(
    value: () -> Float,
    modifier: Modifier = Modifier,
    direction: FillDirection = FillDirection.LeftToRight,
    track: FrameKey = SkinnedKeys.BarTrack,
    fill: BarKey = SkinnedKeys.BarFill,
    colors: SkinnedBarColors = SkinnedBarColors(),
) {
    Box(modifier.skinnedBar(track, fill, value, direction, colors))
}
