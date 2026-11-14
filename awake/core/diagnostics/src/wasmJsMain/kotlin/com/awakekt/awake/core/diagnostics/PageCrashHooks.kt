/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.ErrorEvent
import org.w3c.dom.events.Event

/**
 * The page lifecycle hooks [installPageCrashHooks] put on the page: a clean leave ends the session, a
 * hidden tab records a breadcrumb, and uncaught errors and rejections go to [CrashRecorder.error].
 * [close] turns them off; the listeners stay on the page but do nothing after it.
 */
class PageCrashHooks internal constructor() : AutoCloseable {
    internal var open = true
        private set

    /** Turns the hooks off. Closing twice is harmless. */
    override fun close() {
        open = false
    }
}

/**
 * Hooks [recorder] to the page, so a web app records what a tab kill would otherwise lose:
 *
 * - `pagehide`: the page is being left on purpose (closed, reloaded, navigated away), so the session
 *   [ends][CrashRecorder.end] cleanly and the next start finds no crash. A page restored from the
 *   back-forward cache should call [CrashRecorder.begin] again.
 * - `visibilitychange` to hidden: [hiddenCrumb] is recorded, since a mobile browser may kill a hidden
 *   tab with no further event; by default a breadcrumb that says only that the tab was hidden.
 * - `error` and `unhandledrejection`: the message goes to [CrashRecorder.error] under the source
 *   `"window"` or `"rejection"`, when [sampler] admits it (keyed by source and message); with no
 *   sampler every one is recorded, and the recorder throttles its own writes.
 *
 * The app still calls [CrashRecorder.begin] at start and [CrashRecorder.record] for its periodic
 * breadcrumbs; these hooks only cover the page events it can't see from a frame loop.
 *
 * @param recorder The app's recorder, which the app has already begun.
 * @param sampler Admits the errors worth recording; null records every one.
 * @param now The current time in epoch milliseconds, for the hidden breadcrumb.
 * @param hiddenCrumb The breadcrumb recorded when the tab is hidden.
 * @return The installed hooks, which [PageCrashHooks.close] turns off.
 */
fun installPageCrashHooks(
    recorder: CrashRecorder,
    sampler: ErrorSampler? = null,
    now: () -> Long = { epochMillis().toLong() },
    hiddenCrumb: () -> CrashBreadcrumb = { CrashBreadcrumb(at = now(), hidden = true) },
): PageCrashHooks {
    val hooks = PageCrashHooks()
    fun report(source: String, message: String) {
        if (sampler == null || sampler.admit("$source: $message")) recorder.error(source, message)
    }
    window.addEventListener("pagehide") { if (hooks.open) recorder.end() }
    document.addEventListener("visibilitychange") {
        if (hooks.open && pageHidden()) recorder.record(hiddenCrumb())
    }
    window.addEventListener("error") { event -> if (hooks.open) report("window", errorMessage(event)) }
    window.addEventListener("unhandledrejection") { event -> if (hooks.open) report("rejection", rejectionReason(event)) }
    return hooks
}

private fun errorMessage(event: Event): String =
    (event as? ErrorEvent)?.let { error -> "${error.message} (${error.filename}:${error.lineno})" } ?: event.type

// The event is read in the JavaScript body, which detekt doesn't see.
@Suppress("UnusedParameter")
private fun rejectionReason(event: Event): String = js("String(event.reason)")

private fun epochMillis(): Double = js("Date.now()")

private fun pageHidden(): Boolean = js("document.visibilityState === 'hidden'")
