/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.skin

import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.FillDirection
import com.awakekt.awake.compose.ui.graphics.drawImageFill
import com.awakekt.awake.compose.ui.graphics.drawImageFillBar

/**
 * The skin components draw from, provided like a theme: `LocalSkin provides skin`. `null`, the
 * default, means no skin, and a component keeps its token-based look.
 */
val LocalSkin = compositionLocalOf<Skin?> { null }

context(_: Composer)
/**
 * Draws the [key] frame behind the node. Without a skin, without that part, or before the sheet
 * has decoded it draws nothing, so chain the token look before it.
 */
fun Modifier.skinPart(key: FrameKey): Modifier {
    val skin = LocalSkin.current ?: return this
    return drawBehind { skin.resolve(key)?.let { drawImageFill(it) } }
}

context(_: Composer)
/**
 * Draws the [key] frame for the state [source], [enabled] and [selected] give, behind the node:
 * normal, hovered, pressed or disabled, falling back as [com.awakekt.awake.compose.foundation.sheet.StateSheet.fill]
 * says. Read each draw, so a state change shows with no recomposition. Draws nothing without a
 * skin, without that part, or before the sheet has decoded.
 */
fun Modifier.skinPart(
    key: StatesKey,
    source: InteractionSource,
    enabled: Boolean = true,
    selected: Boolean = false,
): Modifier {
    val skin = LocalSkin.current ?: return this
    return drawBehind { skin.resolve(key)?.fill(source, enabled, selected)?.let { drawImageFill(it) } }
}

context(_: Composer)
/**
 * Draws the [key] bar filled to [value] (0..1, read each draw) from the edge [direction] names,
 * with its end caps whole. Draws nothing without a skin, that part, or a decoded sheet.
 */
fun Modifier.skinBar(key: BarKey, value: () -> Float, direction: FillDirection = FillDirection.LeftToRight): Modifier {
    val skin = LocalSkin.current ?: return this
    return drawBehind { skin.resolve(key)?.let { drawImageFillBar(it, value(), direction) } }
}
