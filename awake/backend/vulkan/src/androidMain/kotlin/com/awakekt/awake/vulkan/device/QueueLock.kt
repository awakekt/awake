/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

internal actual class QueueLock {
    actual inline fun <T> withLock(action: () -> T): T = synchronized(this, action)
}
