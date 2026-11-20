/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CiTestCoverageChecksTest {
    private val task = ":awake:compose:ui:desktopTest"

    @Test
    fun aSuiteRunWithoutAFilterRunsEveryClass() {
        val runs = CiTestRuns(listOf("        run: ./gradlew :awake:compose:ui:desktopTest :awake:compose:foundation:desktopTest\n"), task)
        assertTrue(runs.wholeSuite)
        assertEquals(emptyList(), unrunTestClasses(runs, listOf("FrameStepTest", "ComposeHostTest")))
    }

    @Test
    fun aFilteredRunCoversOnlyTheClassesItNames() {
        val workflow = """
            run: >-
              ./gradlew
              :awake:compose:ui:desktopTest --tests com.awakekt.awake.compose.ui.ComposeHostTest
              --tests 'com.awakekt.awake.compose.ui.Painter*'
        """.trimIndent()
        val runs = CiTestRuns(listOf(workflow), task)
        assertFalse(runs.wholeSuite)
        assertEquals(listOf("FrameStepTest"), unrunTestClasses(runs, listOf("FrameStepTest", "ComposeHostTest", "Painter")))
    }

    @Test
    fun aCommentThatMentionsTheTaskRunsNothing() {
        val runs = CiTestRuns(listOf("      # :awake:compose:ui:desktopTest is run below\n      run: echo none\n"), task)
        assertFalse(runs.wholeSuite)
        assertEquals(listOf("FrameStepTest"), unrunTestClasses(runs, listOf("FrameStepTest")))
    }

    @Test
    fun onlyAFileWithATestIsATestClass() {
        assertEquals("FrameStepTest", testClassName("FrameStepTest.kt", "class FrameStepTest {\n    @Test\n    fun steps() {}\n}"))
        assertNull(testClassName("FakeApplier.kt", "class FakeApplier : Applier"))
    }
}
