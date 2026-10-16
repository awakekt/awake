/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.unicode

import com.awakekt.awake.core.host.readResourceBytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every case of Unicode's own `GraphemeBreakTest.txt`, the file `tools/unicode/generate_grapheme_break_table.py`
 * copies from the same Unicode version as the table.
 */
class GraphemeBreakConformanceTest {

    @Test
    fun everyUnicodeTestCaseBreaksWhereUnicodeSays() = runTest {
        val cases = readCases()
        assertTrue(cases.size > 800, "read ${cases.size} cases")
        val failures = cases.mapNotNull { case ->
            val found = buildList {
                add(0)
                GraphemeClusters.forEach(case.text) { _, end -> add(end) }
            }
            if (found == case.boundaries) null else "${case.line}\n  expected ${case.boundaries}, found $found"
        }
        if (failures.isNotEmpty()) fail("${failures.size} of ${cases.size} cases break wrongly:\n${failures.take(20).joinToString("\n")}")
    }

    @Test
    fun theOtherQueriesAgreeWithForwardIteration() = runTest {
        for (case in readCases()) {
            val text = case.text
            assertEquals(case.boundaries.size - 1, GraphemeClusters.count(text), case.line)
            for (index in 0..text.length) {
                val isBoundary = index in case.boundaries
                assertEquals(isBoundary, GraphemeClusters.isBoundary(text, index), "isBoundary($index) in ${case.line}")
                val floor = case.boundaries.last { it <= index }
                assertEquals(floor, GraphemeClusters.floorBoundary(text, index), "floorBoundary($index) in ${case.line}")
                val ceil = case.boundaries.first { it >= index }
                assertEquals(ceil, GraphemeClusters.ceilBoundary(text, index), "ceilBoundary($index) in ${case.line}")
                if (index > 0) {
                    val previous = case.boundaries.last { it < index }
                    assertEquals(previous, GraphemeClusters.previousBoundary(text, index), "previousBoundary($index) in ${case.line}")
                }
            }
        }
    }

    @Test
    fun theTableIsTheTestDataVersion() = runTest {
        val header = readResourceBytes(TEST_FILE).decodeToString().lineSequence().first()
        assertTrue(header.contains("-${GraphemeClusters.unicodeVersion}"), header)
    }

    private class Case(val line: String, val text: String, val boundaries: List<Int>)

    private suspend fun readCases(): List<Case> =
        readResourceBytes(TEST_FILE).decodeToString().lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map(::parse)
            .toList()

    /** `÷ 0061 × 0308 ÷ 0062 ÷`: break marks between hex code points. */
    private fun parse(line: String): Case {
        val text = StringBuilder()
        val boundaries = mutableListOf<Int>()
        for (token in line.split(' ', '\t').filter { it.isNotEmpty() }) {
            when (token) {
                BREAK -> boundaries += text.length
                NO_BREAK -> Unit
                else -> text.append(CodePoints.asString(token.toInt(HEX)))
            }
        }
        return Case(line, text.toString(), boundaries)
    }

    private companion object {
        const val TEST_FILE = "unicode/GraphemeBreakTest.txt"
        const val BREAK = "\u00F7"
        const val NO_BREAK = "\u00D7"
        const val HEX = 16
    }
}
