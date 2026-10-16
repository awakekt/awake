/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.unicode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GraphemeClustersTest {

    private fun clusters(text: String): List<String> = buildList {
        GraphemeClusters.forEach(text) { start, end -> add(text.substring(start, end)) }
    }

    @Test
    fun emojiSequencesAreOneClusterEach() {
        assertEquals(listOf(FAMILY), clusters(FAMILY))
        assertEquals(listOf(THUMBS_UP_MEDIUM), clusters(THUMBS_UP_MEDIUM))
        assertEquals(listOf(KEYCAP_ONE), clusters(KEYCAP_ONE))
        assertEquals(listOf("a", FAMILY, "b", THUMBS_UP_MEDIUM, KEYCAP_ONE), clusters("a${FAMILY}b$THUMBS_UP_MEDIUM$KEYCAP_ONE"))
    }

    @Test
    fun regionalIndicatorsPairIntoFlags() {
        assertEquals(listOf(FLAG_PH, FLAG_JP), clusters(FLAG_PH + FLAG_JP))
        // An odd one out stands alone after the pairs.
        val odd = FLAG_PH + FLAG_JP.substring(0, 2)
        assertEquals(listOf(FLAG_PH, FLAG_JP.substring(0, 2)), clusters(odd))
    }

    @Test
    fun combiningMarksStayWithTheirBase() {
        val composed = "e\u0301"
        assertEquals(listOf("n", composed, "e"), clusters("n${composed}e"))
        assertEquals(listOf("a\u0308\u0301"), clusters("a\u0308\u0301"))
    }

    @Test
    fun hangulJamoSequencesAndSyllablesAreOneCluster() {
        // ᄀ ᅡ ᆨ (L V T) is the conjoining spelling of 각.
        val jamo = "\u1100\u1161\u11A8"
        assertEquals(listOf(jamo, "\uAC01"), clusters(jamo + "\uAC01"))
        // 가 (LV) takes a trailing jamo; 각 (LVT) takes another T but not a V.
        assertEquals(listOf("\uAC00\u11A8"), clusters("\uAC00\u11A8"))
        assertEquals(listOf("\uAC01", "\u1161"), clusters("\uAC01\u1161"))
    }

    @Test
    fun crLfIsOneClusterAndLineBreaksStandAlone() {
        assertEquals(listOf("a", "\r\n", "b", "\n", "\n"), clusters("a\r\nb\n\n"))
    }

    @Test
    fun indicConjunctsJoinThroughTheVirama() {
        // क्त: KA, VIRAMA, TA is one cluster under GB9c.
        assertEquals(listOf("\u0915\u094D\u0924"), clusters("\u0915\u094D\u0924"))
    }

    @Test
    fun stepsAcrossClustersBothWays() {
        val text = "a${FAMILY}b"
        assertEquals(1, GraphemeClusters.nextBoundary(text, 0))
        assertEquals(1 + FAMILY.length, GraphemeClusters.nextBoundary(text, 1))
        assertEquals(1, GraphemeClusters.previousBoundary(text, 1 + FAMILY.length))
        assertEquals(1, GraphemeClusters.floorBoundary(text, 4))
        assertEquals(1 + FAMILY.length, GraphemeClusters.ceilBoundary(text, 4))
        assertEquals(0, GraphemeClusters.previousBoundary(text, 0))
        assertEquals(text.length, GraphemeClusters.nextBoundary(text, text.length))
        assertEquals(3, GraphemeClusters.count(text))
        assertEquals(0, GraphemeClusters.count(""))
    }

    @Test
    fun aLoneSurrogateIsItsOwnCluster() {
        assertEquals(listOf("a", "\uD83D", "b"), clusters("a\uD83Db"))
    }

    @Test
    fun codePointsDecodePairsAndRoundTrip() {
        assertEquals(0x1F600, CodePoints.at("\uD83D\uDE00", 0))
        assertEquals(0xDE00, CodePoints.at("\uD83D\uDE00", 1))
        assertEquals("\uD83D\uDE00", CodePoints.asString(0x1F600))
        assertEquals("\uD840\uDC00", CodePoints.asString(0x20000))
        assertEquals(2, CodePoints.charCount(0x1F600))
        assertFailsWith<IllegalArgumentException> { CodePoints.asString(0x110000) }
    }

    companion object {
        /** 👨‍👩‍👧: man, ZWJ, woman, ZWJ, girl. */
        const val FAMILY = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"

        /** 👍🏽: thumbs up and a medium skin tone. */
        const val THUMBS_UP_MEDIUM = "\uD83D\uDC4D\uD83C\uDFFD"

        /** 1️⃣: digit one, VS16, combining enclosing keycap. */
        const val KEYCAP_ONE = "1\uFE0F\u20E3"

        /** 🇵🇭 and 🇯🇵: two regional indicators each. */
        const val FLAG_PH = "\uD83C\uDDF5\uD83C\uDDED"
        const val FLAG_JP = "\uD83C\uDDEF\uD83C\uDDF5"
    }
}
