/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

/**
 * Optional [Transport] capability that tears down a stalled or unresponsive socket immediately
 * without waiting for a graceful close handshake, unblocking any coroutine suspended in
 * [Transport.send].
 */
interface AbortableTransport : Transport {
    /** Immediately cancels the underlying socket and transitions [state] to [ConnectionState.Disconnected]. */
    fun abort()
}
