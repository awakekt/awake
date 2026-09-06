/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

import kotlinx.cinterop.Arena
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock

@OptIn(ExperimentalForeignApi::class)
internal actual class PlatformLock {
    private val arena = Arena()
    private val mutex: pthread_mutex_t = arena.alloc()

    init {
        pthread_mutex_init(mutex.ptr, null)
    }

    actual inline fun <T> withLock(action: () -> T): T {
        pthread_mutex_lock(mutex.ptr)
        try {
            return action()
        } finally {
            pthread_mutex_unlock(mutex.ptr)
        }
    }
}
