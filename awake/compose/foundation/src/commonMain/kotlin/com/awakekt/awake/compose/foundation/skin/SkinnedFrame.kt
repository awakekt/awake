/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "MatchingDeclarationName")

package com.awakekt.awake.compose.foundation.skin

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxScope
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color

/**
 * Content padding in dp, per side.
 *
 * @property start The leading side.
 * @property top The top side.
 * @property end The trailing side.
 * @property bottom The bottom side.
 */
class FramePadding(val start: Dp, val top: Dp, val end: Dp, val bottom: Dp) {
    /** The same padding on every side. */
    constructor(all: Dp) : this(all, all, all, all)
}

/**
 * A panel drawn as the nine-slice [part] of [LocalSkin] around [content], or as the [fallback]
 * colour with no skin or no such part.
 *
 * Content is inset by the part's slice insets, so it sits inside the art's border, unless
 * [padding] overrides it. The insets are read from the skin's declaration, not its decoded image,
 * so the layout is right from the first frame. With no part and no [padding], content is not inset.
 */
context(_: Composer)
fun SkinnedFrame(
    modifier: Modifier = Modifier,
    part: FrameKey = SkinnedKeys.Frame,
    fallback: Color = Color(0.12f, 0.13f, 0.17f),
    padding: FramePadding? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: (
        context(Composer)
        BoxScope.() -> Unit
    )? = null,
) {
    val skin = LocalSkin.current
    val insets = skin?.sliceInsets(part)
    val inner = when {
        padding != null -> padding
        insets != null -> {
            val unit = skin.pixelsPerDp
            FramePadding((insets.left / unit).dp, (insets.top / unit).dp, (insets.right / unit).dp, (insets.bottom / unit).dp)
        }
        else -> null
    }
    val framed = modifier.skinnedFrame(part, fallback)
    Box(if (inner != null) framed.padding(inner.start, inner.top, inner.end, inner.bottom) else framed, contentAlignment, content)
}
