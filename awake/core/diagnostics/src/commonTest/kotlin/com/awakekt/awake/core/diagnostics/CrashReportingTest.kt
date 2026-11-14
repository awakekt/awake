/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The sender seam sends a pending crash once, and the sampler rate-limits and deduplicates errors. */
class CrashReportingTest {
    private var clock = 1_000L
    private val storage = InMemoryCrashStorage()

    @Test
    fun aPendingCrashIsSentOnceAndStaysPendingWhenTheSendFails() = runTest {
        crashOnce()
        val recorder = CrashRecorder(storage, now = { clock })
        recorder.begin(CrashSessionMeta(build = "2"))
        val sent = mutableListOf<CrashSession>()

        assertFailsWith<IllegalStateException> { recorder.sendPending { error("offline") } }
        assertNotNull(recorder.pending(), "a failed send leaves the report pending")

        assertTrue(recorder.sendPending { sent += it })
        assertEquals("1", sent.single().meta.build)
        assertNull(recorder.pending())
        assertFalse(recorder.sendPending { sent += it }, "nothing is pending any more")
    }

    @Test
    fun theSamplerRefusesARepeatedKeyUntilItsDedupeTimePasses() {
        val sampler = ErrorSampler(now = { clock }, dedupeMillis = 500L)

        assertTrue(sampler.admit("window: boom"))
        assertFalse(sampler.admit("window: boom"))
        assertTrue(sampler.admit("window: other"))
        clock += 500L
        assertTrue(sampler.admit("window: boom"))
        assertEquals(1L, sampler.refused)
    }

    @Test
    fun theSamplerAdmitsAtMostItsWindowLimit() {
        val sampler = ErrorSampler(now = { clock }, maxPerWindow = 2, windowMillis = 1_000L, dedupeMillis = 0L)

        assertTrue(sampler.admit("a"))
        assertTrue(sampler.admit("b"))
        assertFalse(sampler.admit("c"))
        clock += 1_000L
        assertTrue(sampler.admit("c"), "a new window admits again")
        assertEquals(1L, sampler.refused)
        sampler.clear()
        assertEquals(0L, sampler.refused)
    }

    @Test
    fun theSamplerForgetsItsOldestKeysPastItsLimit() {
        val sampler = ErrorSampler(now = { clock }, maxPerWindow = 100, maxKeys = 2)

        sampler.admit("a")
        sampler.admit("b")
        sampler.admit("c")

        assertTrue(sampler.admit("a"), "the oldest key was forgotten")
        assertFalse(sampler.admit("c"))
    }

    @Test
    fun theSamplerRefusesBadLimits() {
        assertFailsWith<IllegalArgumentException> { ErrorSampler(now = { 0L }, maxPerWindow = 0) }
        assertFailsWith<IllegalArgumentException> { ErrorSampler(now = { 0L }, windowMillis = 0L) }
        assertFailsWith<IllegalArgumentException> { ErrorSampler(now = { 0L }, dedupeMillis = -1L) }
        assertFailsWith<IllegalArgumentException> { ErrorSampler(now = { 0L }, maxKeys = 0) }
    }

    /** A first run that records a breadcrumb and never ends: the tab was killed. */
    private fun crashOnce() {
        val first = CrashRecorder(storage, now = { clock })
        first.begin(CrashSessionMeta(build = "1"))
        first.record(CrashBreadcrumb(at = clock, fps = 60))
    }
}
