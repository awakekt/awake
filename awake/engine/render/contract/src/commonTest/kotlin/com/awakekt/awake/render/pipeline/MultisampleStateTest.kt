/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultisampleStateTest {
    @Test
    fun unsupportedRequestsDowngradeWithoutIncreasingTheRequestedCost() {
        val reports = mutableListOf<String>()
        val webCounts = setOf(1, 4)
        assertEquals(4, MultisampleState(8).supportedBy(webCounts, reports::add).count)
        assertEquals(1, MultisampleState(2).supportedBy(webCounts, reports::add).count)
        assertEquals(2, reports.size)
        assertTrue(reports[0].contains("requested=8 selected=4"))
        assertTrue(reports[1].contains("requested=2 selected=1"))
    }

    @Test
    fun fallbackClearsCoverageAndSupportedRequestsAreSilent() {
        val reports = mutableListOf<String>()
        assertEquals(MultisampleState(4, true), MultisampleState(4, true).supportedBy(setOf(1, 4), reports::add))
        assertTrue(reports.isEmpty())
        val fallback = MultisampleState(4, true).supportedBy(emptySet(), reports::add)
        assertEquals(1, fallback.count)
        assertFalse(fallback.alphaToCoverageEnabled)
        assertEquals(1, reports.size)
    }

    @Test
    fun invalidCountsAndSingleSampleCoverageAreRejected() {
        listOf(-1, 0, 3, 16).forEach { count -> assertFailsWith<IllegalArgumentException> { MultisampleState(count) } }
        assertFailsWith<IllegalArgumentException> { MultisampleState(1, true) }
    }
}
