/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.interaction.Interaction
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.skin.LocalSkin
import com.awakekt.awake.compose.foundation.skin.SheetRect
import com.awakekt.awake.compose.foundation.skin.StatesKey
import com.awakekt.awake.compose.foundation.skin.skin
import com.awakekt.awake.compose.foundation.skin.skinPart
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

private val Face = StatesKey("button.face")

class SkinnedButtonTest {
    // Four 4 x 4 frames side by side: Normal, Hovered, Pressed, Disabled; one solid colour each.
    private val sheet = ImageBitmap(
        16, 4,
        ByteArray(16 * 4 * 4) { i ->
            val colour = COLOURS[(i / 4) % 16 / 4]
            val channel = i % 4
            if (channel == 3) 255.toByte() else ((colour shr (16 - 8 * channel)) and 0xFF).toByte()
        },
    )
    private val skin = skin(sheet) { states(Face, SheetRect(0, 0, 16, 4)) }

    @Test
    fun aSkinnedButtonShowsItsFourStatesAtEveryDensity() {
        for (density in listOf(1f, 1.5f)) {
            val source = InteractionSource()
            var enabled = true
            val session = composeTestSession(width = 24, height = 24, density = density) {
                CompositionLocalProvider(LocalSkin provides skin) {
                    Box(Modifier.size(8.dp).skinPart(Face, source, enabled))
                }
            }
            val size = 24
            fun centre(): Int = session.frame().primitives.rasterize(size, size).let { px ->
                val o = (3 * size + 3) * 4
                ((px[o].toInt() and 0xFF) shl 16) or ((px[o + 1].toInt() and 0xFF) shl 8) or (px[o + 2].toInt() and 0xFF)
            }

            assertEquals(COLOURS[0], centre(), "normal at $density")
            source.tryEmit(Interaction.Hover.Enter)
            assertEquals(COLOURS[1], centre(), "hovered at $density")
            source.tryEmit(Interaction.Press.Press)
            assertEquals(COLOURS[2], centre(), "pressed at $density")
            enabled = false
            assertEquals(COLOURS[3], centre(), "disabled at $density")
        }
    }

    private companion object {
        val COLOURS = listOf(0xC03030, 0x30C030, 0x3030C0, 0x808080)
    }
}
