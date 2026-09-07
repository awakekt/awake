/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.logging

import platform.Foundation.NSLog

/**
 * Writes records to iOS's native NSLog system, which routes to Console.app and Xcode's
 * debugger console. This sink is essential for iOS because:
 *
 * 1. NSLog output appears in Xcode's debugger console when running simulator or device
 * 2. Logs are captured by Console.app on connected devices
 * 3. stdout/println on iOS go nowhere visible during normal debugging
 *
 * The iOS console visibility is critical for troubleshooting blank screen issues and
 * verifying that exception handling code is executing at all.
 */
class NSLogSink(
    /** Records below this are dropped by the sink, independent of [Log.minimumLevel]. */
    var minimumLevel: LogLevel = LogLevel.Debug,
) : LogSink {

    override fun emit(record: LogRecord) {
        if (record.level < minimumLevel) return
        NSLog(format(record))
        record.cause?.let { NSLog(it.stackTraceToString()) }
    }

    private fun format(record: LogRecord): String {
        val frame = record.frame.toString().padStart(FRAME_DIGITS, '0')
        return "[Awake] f$frame ${record.level.initial}  ${record.tag}  ${record.message}"
    }

    private companion object {
        /** Five digits covers about ninety minutes at 60fps before the column widens. */
        const val FRAME_DIGITS = 5
    }
}
