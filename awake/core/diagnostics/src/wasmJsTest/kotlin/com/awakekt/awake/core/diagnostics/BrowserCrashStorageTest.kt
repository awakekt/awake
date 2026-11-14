/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlinx.browser.window
import org.w3c.dom.events.Event
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A crash survives in localStorage, and the page hooks end the session and record errors. */
class BrowserCrashStorageTest {
    private val prefix = "awake.test.crash"

    @AfterTest
    fun clean() {
        window.localStorage.removeItem("$prefix.session")
        window.localStorage.removeItem("$prefix.pending")
    }

    @Test
    fun localStorageKeepsWhatItWasGiven() {
        val storage = LocalStorageCrashStorage()
        assertTrue(storage.available)

        storage.write("$prefix.session", "{}")
        assertEquals("{}", LocalStorageCrashStorage().read("$prefix.session"))
        storage.remove("$prefix.session")
        assertNull(storage.read("$prefix.session"))
    }

    @Test
    fun aKilledSessionIsFoundByTheNextRun() {
        val first = CrashRecorder(LocalStorageCrashStorage(), now = { 1L }, keyPrefix = prefix)
        first.begin(CrashSessionMeta(build = "1"))
        first.record(CrashBreadcrumb(at = 1L))

        val crashed = CrashRecorder(LocalStorageCrashStorage(), now = { 2L }, keyPrefix = prefix).begin(CrashSessionMeta(build = "2"))

        assertEquals("1", crashed?.meta?.build)
    }

    @Test
    fun pageHooksEndTheSessionOnPageHideAndRecordErrorsUntilClosed() {
        val recorder = CrashRecorder(InMemoryCrashStorage(), now = { 5L }, keyPrefix = prefix)
        recorder.begin(CrashSessionMeta(build = "1"))
        val hooks = installPageCrashHooks(recorder, now = { 5L })

        window.dispatchEvent(Event("unhandledrejection"))
        assertEquals(1, recorder.recentErrors.size)
        assertTrue(recorder.recentErrors.single().text.startsWith("rejection: "))

        window.dispatchEvent(Event("pagehide"))
        assertNull(recorder.sessionId, "a clean leave ends the session")

        hooks.close()
        recorder.begin(CrashSessionMeta(build = "2"))
        window.dispatchEvent(Event("unhandledrejection"))
        assertEquals(0, recorder.recentErrors.size, "closed hooks record nothing")
    }
}
