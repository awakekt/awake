/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

/**
 * Re-entrant mutual exclusion lock used to guard container resolution and teardown across threads.
 */
internal expect class DiLock() {
    fun <T> withLock(action: () -> T): T
}
