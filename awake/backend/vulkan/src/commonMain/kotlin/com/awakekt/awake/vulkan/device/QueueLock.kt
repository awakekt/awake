/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

/**
 * Re-entrant mutual exclusion lock protecting Vulkan host-synchronized queues and shared
 * transfer command resources when background coroutine attachments run concurrently with render frames.
 */
internal expect class QueueLock() {
    inline fun <T> withLock(action: () -> T): T
}
