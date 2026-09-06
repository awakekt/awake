/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Manages the lifecycle and [CoroutineScope] of a state container across platforms.
 */
interface StoreScope : AutoCloseable {
    /** The active coroutine scope bound to this store. */
    val coroutineScope: CoroutineScope

    /** Cancels the underlying [coroutineScope] and releases associated resources. */
    override fun close() {
        coroutineScope.cancel()
    }
}

/**
 * Default implementation of [StoreScope] backed by a [SupervisorJob].
 */
class DefaultStoreScope(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : StoreScope {
    private val job = SupervisorJob()
    override val coroutineScope: CoroutineScope = CoroutineScope(job + dispatcher)

    override fun close() {
        coroutineScope.cancel()
    }
}
