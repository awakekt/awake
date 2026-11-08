/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashRecorderTest {
    private val storage = FaultInjectableCrashStorage()
    private var clock = 1_000L
    private var ids = 0
    private val meta = CrashSessionMeta(
        build = "0.43.0",
        commit = "abc1234",
        userAgent = "TestAgent",
        platform = "Linux",
    )

    private fun newRecorder(prefix: String = CrashRecorder.DEFAULT_KEY_PREFIX): CrashRecorder =
        CrashRecorder(
            storage = storage,
            now = { clock },
            capacity = 3,
            keyPrefix = prefix,
            newId = { "session-${++ids}" },
        )

    @Test
    fun retainsNewestBreadcrumbsOldestFirstAndPersistsToStorage() {
        val recorder = newRecorder().also { it.begin(meta) }
        for (second in 1..5) {
            recorder.record(
                CrashBreadcrumb(
                    at = second * 1_000L,
                    wasmBytes = second.toLong(),
                    metrics = mapOf("entities" to second * 10L),
                ),
            )
        }

        assertEquals(listOf(3L, 4L, 5L), recorder.recent.map { it.wasmBytes })
        assertEquals(
            listOf(30L, 40L, 50L),
            storage.storedSession(recorder.sessionKey)?.crumbs?.map { it.metrics["entities"] },
        )
    }

    @Test
    fun promotesUnendedSessionToPendingCrashOnNextBeginUntilMarkedSent() {
        newRecorder().apply {
            assertNull(begin(meta))
            record(CrashBreadcrumb(at = 1L, wasmBytes = 512L))
            error("device-lost", "unknown")
        }

        val next = newRecorder()
        val crashed = assertNotNull(next.begin(meta))
        assertEquals("session-1", crashed.id)
        assertEquals(listOf(512L), crashed.crumbs.map { it.wasmBytes })
        assertEquals(listOf("device-lost: unknown"), crashed.errors.map { it.text })
        assertEquals(crashed, next.pending())
        assertEquals("session-2", next.sessionId)
        assertEquals(clock, next.startedAt)

        next.markSent()
        assertNull(next.pending())
        assertNull(newRecorder().pending())
    }

    @Test
    fun cleanExitAndEmptySessionAreNotReportedAsCrashes() {
        newRecorder().apply {
            begin(meta)
            record(CrashBreadcrumb(at = 1L))
            end()
            record(CrashBreadcrumb(at = 2L))
        }
        val afterClean = newRecorder()
        assertNull(afterClean.begin(meta))
        assertNull(afterClean.pending())

        // Session that recorded zero breadcrumbs and zero errors is not reported.
        assertNull(newRecorder().begin(meta))
    }

    @Test
    fun restartingSessionOnSameRecorderEndsPreviousCleanly() {
        val recorder = newRecorder().apply {
            begin(meta)
            record(CrashBreadcrumb(at = 1L))
        }
        assertNull(recorder.begin(meta))
        recorder.record(CrashBreadcrumb(at = 2L, scene = "harbor"))

        val crashed = assertNotNull(newRecorder().begin(meta))
        assertEquals(listOf("harbor"), crashed.crumbs.map { it.scene })
    }

    @Test
    fun countsErrorsIntoNextBreadcrumbTruncatesTextAndThrottlesFloodWrites() {
        val recorder = newRecorder().also { it.begin(meta) }
        clock += 2_000L
        recorder.error("page", "x".repeat(1_000))
        assertEquals(1, storage.storedSession(recorder.sessionKey)?.errors?.size)

        recorder.error("page", "second")
        assertEquals(1, storage.storedSession(recorder.sessionKey)?.errors?.size)

        clock += 1_000L
        recorder.error("page", "third")
        assertEquals(3, storage.storedSession(recorder.sessionKey)?.errors?.size)

        recorder.record(CrashBreadcrumb(at = clock))
        recorder.record(CrashBreadcrumb(at = clock + 1_000L))
        assertEquals(listOf(3, 0), recorder.recent.map { it.errors })
        assertEquals(CrashRecorder.ERROR_CHARS, recorder.recentErrors.first().text.length)
    }

    @Test
    fun updatesAdapterIgnoresCorruptOrFailingStorageAndValidatesArguments() {
        val recorder = newRecorder(prefix = "custom.crash").also { it.begin(meta) }
        assertEquals("custom.crash.session", recorder.sessionKey)
        assertEquals("custom.crash.pending", recorder.pendingKey)

        recorder.adapter("Discrete GPU")
        assertEquals("Discrete GPU", storage.storedSession(recorder.sessionKey)?.meta?.gpu)

        storage.failWrites = true
        recorder.record(CrashBreadcrumb(at = 1L))
        assertTrue(recorder.recent.isNotEmpty())
        storage.failWrites = false

        storage.write(recorder.sessionKey, "{corrupt json")
        assertNull(newRecorder(prefix = "custom.crash").begin(meta))

        val memory = InMemoryCrashStorage()
        memory.write("k", "v")
        assertEquals("v", memory.read("k"))
        memory.remove("k")
        assertNull(memory.read("k"))

        assertFailsWith<IllegalArgumentException> {
            CrashRecorder(memory, now = { 0L }, capacity = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            CrashRecorder(memory, now = { 0L }, keyPrefix = "   ")
        }
    }

    private class FaultInjectableCrashStorage : CrashStorage {
        private val delegate = InMemoryCrashStorage()
        var failWrites = false

        override fun read(key: String): String? = delegate.read(key)

        override fun write(key: String, value: String) {
            check(!failWrites) { "QuotaExceededError" }
            delegate.write(key, value)
        }

        override fun remove(key: String) {
            delegate.remove(key)
        }

        fun storedSession(key: String): CrashSession? =
            delegate.read(key)?.let { Json.decodeFromString(CrashSession.serializer(), it) }
    }
}
