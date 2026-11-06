/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PipelineLoadContextTest {
    @Test
    fun aFailedBuildNamesItsPipeline() = runTest {
        val failure = assertFailsWith<IllegalStateException> {
            withPipelineLoadContext("shadow") { error("shader module rejected") }
        }

        assertEquals("Failed to build pipeline 'shadow': shader module rejected", failure.message)
    }

    /** A cancelled engine start must stop, not report a pipeline that failed to build. */
    @Test
    fun aCancellationGoesThroughUnwrapped() = runTest {
        val cancelled = CancellationException("engine start cancelled")

        val thrown = assertFailsWith<CancellationException> {
            withPipelineLoadContext("shadow") { throw cancelled }
        }

        assertIs<CancellationException>(thrown)
        assertEquals("engine start cancelled", thrown.message)
    }
}
