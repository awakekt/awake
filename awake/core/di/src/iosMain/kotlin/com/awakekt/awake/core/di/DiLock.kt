/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

import platform.Foundation.NSRecursiveLock

internal actual class DiLock {
    private val lock = NSRecursiveLock()

    actual fun <T> withLock(action: () -> T): T {
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}
