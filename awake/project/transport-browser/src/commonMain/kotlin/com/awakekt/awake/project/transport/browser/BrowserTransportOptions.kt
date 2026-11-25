/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.transport.browser

/**
 * Timeout, retry, and streaming progress settings for [BrowserContentTransport].
 *
 * Every timeout applies to a single `fetch()` attempt and aborts the in-flight request via
 * `AbortController` when exceeded; timed-out or network-failed attempts are retried up to
 * [maxAttempts] with exponential backoff starting at [retryBaseDelayMs].
 *
 * @property connectTimeoutMs Maximum time in milliseconds to wait for response headers before
 * aborting the attempt. Must be strictly positive.
 * @property stallTimeoutMs Maximum time in milliseconds allowed between consecutive response body
 * chunks before aborting the attempt. Reset on every received chunk. Must be strictly positive.
 * @property totalTimeoutMs Maximum end-to-end time in milliseconds allowed for a single attempt
 * (connect plus full response body). Must be strictly positive.
 * @property maxAttempts Maximum number of attempts per request, including the initial try. Must be
 * at least 1; `1` disables retries inside the transport.
 * @property retryBaseDelayMs Base delay in milliseconds before the first retry when the server does
 * not specify `Retry-After`. Subsequent retries double this delay (capped). Must be non-negative.
 * @property onChunk Optional callback invoked as response body bytes arrive during streaming (or
 * once when streaming is unavailable and `response.arrayBuffer()` is used). Receives the sanitized
 * request URL (with any query string stripped so read tokens are never exposed) and the cumulative
 * number of bytes read so far for the current attempt.
 */
data class BrowserTransportOptions(
    val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    val stallTimeoutMs: Long = DEFAULT_STALL_TIMEOUT_MS,
    val totalTimeoutMs: Long = DEFAULT_TOTAL_TIMEOUT_MS,
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val retryBaseDelayMs: Long = DEFAULT_RETRY_BASE_DELAY_MS,
    val onChunk: ((url: String, bytesRead: Int) -> Unit)? = null,
) {
    init {
        require(connectTimeoutMs > 0L) { "connectTimeoutMs must be positive: $connectTimeoutMs" }
        require(stallTimeoutMs > 0L) { "stallTimeoutMs must be positive: $stallTimeoutMs" }
        require(totalTimeoutMs > 0L) { "totalTimeoutMs must be positive: $totalTimeoutMs" }
        require(maxAttempts >= 1) { "maxAttempts must be at least 1: $maxAttempts" }
        require(retryBaseDelayMs >= 0L) { "retryBaseDelayMs must not be negative: $retryBaseDelayMs" }
    }

    /** Default timeout, retry, and backoff values for [BrowserTransportOptions]. */
    companion object {
        /** Default time allowed to receive response headers, in milliseconds. */
        const val DEFAULT_CONNECT_TIMEOUT_MS: Long = 20_000L

        /** Default time allowed between response body chunks, in milliseconds. */
        const val DEFAULT_STALL_TIMEOUT_MS: Long = 25_000L

        /** Default end-to-end deadline for a single fetch attempt, in milliseconds. */
        const val DEFAULT_TOTAL_TIMEOUT_MS: Long = 120_000L

        /** Default total attempts per request, including the initial try. */
        const val DEFAULT_MAX_ATTEMPTS: Int = 3

        /** Default base delay before the first retry, in milliseconds. */
        const val DEFAULT_RETRY_BASE_DELAY_MS: Long = 600L
    }
}
