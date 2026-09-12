/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BoundedPreparationQueueTest {
    @Test
    fun preparesAllInputsWithBoundedWorkers() = runTest(StandardTestDispatcher()) {
        val queue = BoundedPreparationQueue<Int, Int>(
            scope = this,
            capacity = 2,
            workers = 2,
        ) { value ->
            delay(1)
            value * 2
        }

        val results = (1..6).map { async { queue.submit(it).await() } }.awaitAll()
        queue.close()

        assertEquals((1..6).map { it * 2 }, results)
    }

    @Test
    fun invalidBoundsAreRejected() = runTest {
        assertFailsWith<IllegalArgumentException> {
            BoundedPreparationQueue<Int, Int>(this, capacity = 0) { it }
        }
        assertFailsWith<IllegalArgumentException> {
            BoundedPreparationQueue<Int, Int>(this, capacity = 1, workers = 0) { it }
        }
    }

    @Test
    fun closeCancelsQueuedPreparationResults() = runTest(StandardTestDispatcher()) {
        val queue = BoundedPreparationQueue<Int, Int>(
            scope = this,
            capacity = 2,
            workers = 1,
        ) { value ->
            delay(100)
            value
        }

        val first = queue.submit(1)
        val second = queue.submit(2)
        queue.close()

        assertTrue(first.isCancelled)
        assertTrue(second.isCancelled)
    }
}
