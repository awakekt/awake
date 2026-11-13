/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.skin

import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.sheet.buttonState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.FillDirection
import com.awakekt.awake.compose.ui.graphics.drawImageFill
import com.awakekt.awake.compose.ui.graphics.drawImageFillBar
import com.awakekt.awake.core.color.Color

/*
 * The draw half of the skinned components. A skin that declares a part owns its look, so before
 * its sheet decodes the part draws nothing; only a missing skin or a missing part draws the token
 * colour. The part is resolved inside the draw lambda through Skin.resolve, a cached map read, so
 * a steady-state draw allocates nothing.
 */

context(_: Composer)
internal fun Modifier.skinnedFace(
    key: StatesKey,
    source: InteractionSource,
    enabled: Boolean,
    selected: Boolean,
    colors: SkinnedButtonColors,
): Modifier {
    val skin = LocalSkin.current
    return drawBehind {
        if (skin != null && key in skin) {
            skin.resolve(key)?.fill(source, enabled, selected)?.let { drawImageFill(it) }
        } else {
            drawRect(color = colors.forState(buttonState(source, enabled, selected)))
        }
    }
}

context(_: Composer)
internal fun Modifier.skinnedFrame(key: FrameKey, fallback: Color): Modifier {
    val skin = LocalSkin.current
    return drawBehind {
        if (skin != null && key in skin) skin.resolve(key)?.let { drawImageFill(it) } else drawRect(color = fallback)
    }
}

context(_: Composer)
internal fun Modifier.skinnedBar(
    track: FrameKey,
    fill: BarKey,
    value: () -> Float,
    direction: FillDirection,
    colors: SkinnedBarColors,
): Modifier {
    val skin = LocalSkin.current
    return drawBehind {
        val v = value().coerceIn(0f, 1f)
        if (skin != null && track in skin) skin.resolve(track)?.let { drawImageFill(it) } else drawRect(color = colors.track)
        if (skin != null && fill in skin) {
            skin.resolve(fill)?.let { drawImageFillBar(it, v, direction) }
        } else {
            val w = width.toFloat()
            val h = height.toFloat()
            when (direction) {
                FillDirection.LeftToRight -> drawRect(0f, 0f, w * v, h, colors.fill)
                FillDirection.RightToLeft -> drawRect(w * (1f - v), 0f, w * v, h, colors.fill)
                FillDirection.TopToBottom -> drawRect(0f, 0f, w, h * v, colors.fill)
                FillDirection.BottomToTop -> drawRect(0f, h * (1f - v), w, h * v, colors.fill)
            }
        }
    }
}
