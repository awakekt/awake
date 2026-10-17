/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.text.unicode.GraphemeClusters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Measuring and cutting text one grapheme cluster at a time, never inside one. */
class ClusterMeasureTest {

    private val font = UiFonts.trueSans()
    private val glyphPx = 16f

    @Test
    fun anEmojiSequenceTheFontLacksIsOneStandIn() {
        // The bundled font has no emoji: each sequence draws its stand-in once, however many code points it joins.
        val standIn = font.measureTextWidth("?", glyphPx)
        for (emoji in listOf(FAMILY, THUMBS_UP_MEDIUM, FLAG_PH, "\uD83D\uDE00")) {
            assertEquals(standIn, font.measureTextWidth(emoji, glyphPx), "width of $emoji")
            val drawn = mutableListOf<Int>()
            font.forEachGlyphInCluster(emoji, 0, emoji.length, glyphPx) { codePoint, _ -> drawn += codePoint }
            assertEquals(1, drawn.size, "glyphs drawn for $emoji")
        }
    }

    @Test
    fun aCharacterTheFontHasDrawsItsMarksAfterIt() {
        // A mark the font lacks draws nothing; a keycap's digit still draws.
        val keycap = "1\uFE0F\u20E3"
        assertEquals(font.measureTextWidth("1", glyphPx), font.measureTextWidth(keycap, glyphPx))
        assertEquals(font.advanceFor('a', glyphPx), font.clusterAdvance("a", 0, 1, glyphPx))
    }

    @Test
    fun ellipsizeKeepsTextThatFits() {
        assertEquals("Harbor", font.ellipsize("Harbor", font.measureTextWidth("Harbor", glyphPx), glyphPx))
    }

    @Test
    fun ellipsizeCutsBetweenClustersAndFits() {
        val text = "Hi $FAMILY$THUMBS_UP_MEDIUM$FLAG_PH e\u0301e\u0301 there"
        val full = font.measureTextWidth(text, glyphPx)
        var width = 0f
        while (width < full) {
            val cut = font.ellipsize(text, width, glyphPx)
            assertTrue(font.measureTextWidth(cut, glyphPx) <= width, "\"$cut\" is wider than $width")
            if (cut.isNotEmpty()) {
                assertTrue(cut.endsWith("\u2026"), "\"$cut\" has no ellipsis")
                val kept = cut.removeSuffix("\u2026")
                assertTrue(text.startsWith(kept), "\"$kept\" is not a start of the text")
                assertTrue(GraphemeClusters.isBoundary(text, kept.length), "\"$kept\" splits a cluster")
            }
            width += 1f
        }
    }

    @Test
    fun ellipsizeIsEmptyWhenNotEvenTheEllipsisFits() {
        assertEquals("", font.ellipsize("Harbor Town", 1f, glyphPx))
        assertEquals("Ha...", font.ellipsize("Harbor Town", font.measureTextWidth("Ha...", glyphPx) + 0.01f, glyphPx, ellipsis = "..."))
    }

    private companion object {
        const val FAMILY = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        const val THUMBS_UP_MEDIUM = "\uD83D\uDC4D\uD83C\uDFFD"
        const val FLAG_PH = "\uD83C\uDDF5\uD83C\uDDED"
    }
}
