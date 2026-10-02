/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The README's showcase table is the catalogue a reader picks from, and it drifted: a showcase
 * shipped that the table never listed. Rows are checked against [EngineShowcases], id and summary.
 */
class EngineShowcaseReadmeTest {
    @Test
    fun theReadmeTableListsEveryShowcaseInOrderWithItsSummary() {
        val rows = File("README.md").readLines()
            .mapNotNull { ROW.matchEntire(it.trim()) }
            .map { it.groupValues[1] to it.groupValues[3] }

        assertEquals(EngineShowcases.map { it.id to it.summary }, rows)
    }

    private companion object {
        /** One catalogue row: a backticked id, a title, then the summary. */
        val ROW = Regex("""\|\s*`([a-z0-9-]+)`\s*\|\s*([^|]+?)\s*\|\s*(.+?)\s*\|""")
    }
}
