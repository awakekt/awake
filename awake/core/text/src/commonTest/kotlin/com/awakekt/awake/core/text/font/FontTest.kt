/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** Preserve assertion details in browser console output even when the runner only reports `Error`. */
internal fun fontTest(name: String, body: suspend TestScope.() -> Unit): TestResult = runTest {
    try {
        body()
    } catch (failure: Throwable) {
        println("FONT_TEST_FAILURE [$name]\n${failure.stackTraceToString()}")
        throw failure
    }
}
