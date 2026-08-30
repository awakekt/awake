/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.font

import io.github.awakelab.awake.core.text.font.GlyphRect
import io.github.awakelab.awake.core.text.font.UiFont
import io.github.awakelab.awake.core.text.font.UiFontSamplingMode
import kotlin.test.Test
import kotlin.test.assertEquals

class UiFontSamplingInfoTest {

    private class FakeFont(
        override val samplingMode: UiFontSamplingMode,
        override val distanceFieldRangePx: Float = 4.0f,
        override val atlasWidth: Int = 512,
        override val atlasHeight: Int = 256,
    ) : UiFont {
        override val cellSize: Int = 16
        override val textScaleStep: Float = 1.0f
        override val atlasPixelsRgba: ByteArray = ByteArray(0)
        override fun uvFor(char: Char): GlyphRect? = null
    }

    @Test
    fun derivesCorrectSamplingInfo() {
        val font = FakeFont(UiFontSamplingMode.DistanceField, distanceFieldRangePx = 6.0f, atlasWidth = 1024, atlasHeight = 1024)
        val info = font.samplingInfo

        assertEquals(UiFontSamplingMode.DistanceField, info.mode)
        assertEquals(6.0f, info.distanceFieldRangePx)
        assertEquals(1024, info.atlasWidth)
        assertEquals(1024, info.atlasHeight)
    }
}
