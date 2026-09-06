/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

/**
 * Platform-agnostic re-entrant or basic mutual exclusion lock.
 */
internal expect class PlatformLock() {
    inline fun <T> withLock(action: () -> T): T
}
