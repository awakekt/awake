/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import kotlin.coroutines.cancellation.CancellationException

/** Throws a [PluginArchiveException] with [message] unless [condition] holds. */
internal inline fun ensureArchive(condition: Boolean, message: () -> String) {
    if (!condition) throw PluginArchiveException(message())
}

/**
 * Runs [block] and keeps any failure as a [Result], except cancellation, which it rethrows. Platform
 * crypto and inflate fail with unrelated types (a JDK `GeneralSecurityException`, a JavaScript
 * error on wasmJs), so callers turn each into a result type rather than naming them.
 */
internal inline fun <T> catchingNonCancellation(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }
