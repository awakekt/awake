/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.unicode

import com.awakekt.awake.core.text.unicode.GraphemeClustersTest.Companion.FAMILY
import com.awakekt.awake.core.text.unicode.GraphemeClustersTest.Companion.FLAG_PH
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextLengthUnitTest {

    private val text = "hi$FAMILY\u00E9$FLAG_PH"

    @Test
    fun eachUnitCountsItsOwnWay() {
        assertEquals(5, TextLengthUnit.Graphemes.count(text))
        // h, i, 5 in the family, é, 2 regional indicators.
        assertEquals(10, TextLengthUnit.CodePoints.count(text))
        // 1 + 1 + (4 + 3 + 4 + 3 + 4) + 2 + 4 + 4
        assertEquals(30, TextLengthUnit.Utf8Bytes.count(text))
        assertEquals(text.encodeToByteArray().size, TextLengthUnit.Utf8Bytes.count(text))
    }

    @Test
    fun aLoneSurrogateCountsAsTheReplacementCharacter() {
        assertEquals(3, TextLengthUnit.Utf8Bytes.count("\uD83D"))
    }

    @Test
    fun fitNeverCutsInsideACluster() {
        for (unit in TextLengthUnit.entries) {
            for (max in 0..TextLengthUnit.Utf8Bytes.count(text)) {
                val end = unit.fit(text, max)
                assertTrue(GraphemeClusters.isBoundary(text, end), "$unit fit($max) = $end splits a cluster")
                assertTrue(unit.count(text.substring(0, end)) <= max, "$unit fit($max) = $end is over")
                if (end < text.length) {
                    val next = GraphemeClusters.nextBoundary(text, end)
                    assertTrue(unit.count(text.substring(0, next)) > max, "$unit fit($max) = $end left room")
                }
            }
        }
    }

    @Test
    fun aWholeClusterOverTheLimitIsLeftOut() {
        // "hi" then the family, which is 5 code points: a 4 code point limit keeps "hi" only.
        assertEquals(2, TextLengthUnit.CodePoints.fit(text, 4))
        assertEquals(2 + FAMILY.length, TextLengthUnit.CodePoints.fit(text, 7))
        assertEquals(3, TextLengthUnit.Graphemes.fit("abc", 9))
        assertEquals(0, TextLengthUnit.Graphemes.fit("abc", 0))
    }
}
