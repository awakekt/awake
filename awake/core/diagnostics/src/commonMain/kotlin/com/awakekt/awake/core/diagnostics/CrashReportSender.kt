/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

/**
 * Where an app sends a crash report: the seam between [CrashRecorder] and a service the engine
 * doesn't own. An app implements it over its own HTTP client, analytics SDK or support channel; Core
 * ships no client and no endpoint.
 */
fun interface CrashReportSender {
    /**
     * Sends [report], suspending until the receiving service has accepted it. Throws when it couldn't
     * be sent, so the report stays pending for the next attempt.
     */
    suspend fun send(report: CrashSession)
}

/**
 * Sends the [pending][CrashRecorder.pending] crash report, if there is one, through [sender], and
 * [marks it sent][CrashRecorder.markSent] once [sender] returns. A send that throws leaves it pending
 * and rethrows, so the app retries on its next start or when it is back online.
 *
 * @return True when a report was sent, false when none was pending.
 */
suspend fun CrashRecorder.sendPending(sender: CrashReportSender): Boolean {
    val report = pending() ?: return false
    sender.send(report)
    markSent()
    return true
}
