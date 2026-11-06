/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.save

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SaveSchedulerTest {

    private data class Account(override val saveKey: String, val balance: Int) : Savable

    @Test
    fun batchSavesSuccessfully() {
        val writes = mutableListOf<List<Account>>()
        val scheduler = SaveScheduler<Account>(
            dispatcher = Dispatchers.Default,
            writer = { batch ->
                writes += batch
                true
            },
        )

        val acc1 = Account("acc1", 100)
        val acc2 = Account("acc2", 200)

        var written = false
        scheduler.save(acc1, acc2) { written = it }

        assertTrue(scheduler.flush(3000L))
        assertTrue(written)
        assertEquals(1, writes.size)
        assertEquals(listOf(acc1, acc2), writes[0])

        scheduler.close()
    }

    @Test
    fun failedSaveCouplesDependenciesForNextWrite() {
        var failFirst = true
        val writeLog = mutableListOf<List<Account>>()

        val scheduler = SaveScheduler<Account>(
            dispatcher = Dispatchers.Default,
            writer = { batch ->
                writeLog += batch
                if (failFirst) {
                    failFirst = false
                    false
                } else {
                    true
                }
            },
        )

        val a = Account("A", 50)
        val b = Account("B", 150)

        scheduler.save(a, b)
        assertTrue(scheduler.flush(3000L))

        // First write failed, so A and B should now be grouped together in the unsaved map.
        // A subsequent write touching only A must carry B with it.
        val aUpdated = Account("A", 60)
        var secondAttemptSuccess = false
        scheduler.save(aUpdated) { secondAttemptSuccess = it }

        assertTrue(scheduler.flush(3000L))
        assertTrue(secondAttemptSuccess)

        assertEquals(2, writeLog.size)
        // First batch had a and b
        assertEquals(listOf("A", "B"), writeLog[0].map { it.saveKey })
        // Second batch included b even though only aUpdated was submitted
        assertEquals(listOf("A", "B"), writeLog[1].map { it.saveKey }.sorted())
        assertEquals(60, writeLog[1].first { it.saveKey == "A" }.balance)

        scheduler.close()
    }

    /** A writer's own timeout fails that write; it must not stop the scheduler saving what comes next. */
    @Test
    fun aWriterThatTimesOutFailsItsWriteAndLaterSavesStillRun() {
        var first = true
        val failures = mutableListOf<Throwable?>()
        val scheduler = SaveScheduler<Account>(
            dispatcher = Dispatchers.Default,
            writer = {
                if (first) {
                    first = false
                    withTimeout(1) { awaitCancellation() }
                }
                true
            },
            onFailed = { _, error -> failures += error },
        )
        var firstWritten: Boolean? = null
        var secondWritten: Boolean? = null

        scheduler.save(Account("A", 1)) { firstWritten = it }
        assertTrue(scheduler.flush(3000L))
        scheduler.save(Account("B", 2)) { secondWritten = it }
        assertTrue(scheduler.flush(3000L), "the scheduler still runs after the timed-out write")

        assertEquals(false, firstWritten)
        assertIs<TimeoutCancellationException>(failures.single())
        assertEquals(true, secondWritten)
        scheduler.close()
    }
}
