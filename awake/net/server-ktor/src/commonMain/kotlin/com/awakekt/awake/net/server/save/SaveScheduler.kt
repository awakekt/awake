/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.save

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Coordinates asynchronous, batched persistence off the main tick thread, maintaining transactional consistency
 * across multi-entity operations (such as trades or item handoffs).
 *
 * @param T The savable data type.
 * @param dispatcher Dispatcher where persistence I/O executes.
 * @param writer Persists a batch of items. Must return `true` on success and `false` on retryable failure.
 * @param onFailed Permanent failure or settlement notification.
 */
class SaveScheduler<T : Savable>(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val writer: suspend (batch: List<T>) -> Boolean,
    private val onFailed: (items: List<T>, error: Throwable?) -> Unit = { _, _ -> },
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val queue = Channel<SaveRequest<T>>(Channel.UNLIMITED)

    // Dependency groups: items that must be persisted atomically together.
    private val unsaved = HashMap<String, MutableMap<String, T>>()

    init {
        scope.launch {
            for (request in queue) {
                process(request)
            }
        }
    }

    private class SaveRequest<T : Savable>(
        val items: List<T>,
        val callback: (Boolean) -> Unit,
    )

    /**
     * Queues [items] to be saved together atomically.
     *
     * @param items One or more items to persist.
     * @param then Optional callback invoked on the save dispatcher after the write attempt completes.
     */
    fun save(vararg items: T, then: (written: Boolean) -> Unit = {}) {
        save(items.toList(), then)
    }

    /**
     * Queues [items] to be saved together atomically.
     *
     * @param items Collection of items to persist.
     * @param then Optional callback invoked on the save dispatcher after the write attempt completes.
     */
    fun save(items: Collection<T>, then: (written: Boolean) -> Unit = {}) {
        if (items.isEmpty()) {
            then(true)
            return
        }
        queue.trySend(SaveRequest(items.toList(), then))
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun process(request: SaveRequest<T>) {
        val batch = LinkedHashMap<String, T>()

        // Merge any existing unsaved dependency groups for these items
        for (item in request.items) {
            val group = unsaved[item.saveKey]
            if (group != null) {
                batch.putAll(group)
            }
            batch[item.saveKey] = item
        }

        val list = batch.values.toList()
        if (list.isEmpty()) {
            request.callback(true)
            return
        }

        var success = false
        var failureCause: Throwable? = null

        try {
            success = writer(list)
        } catch (t: Throwable) {
            failureCause = t
            success = false
        }

        if (success) {
            for (key in batch.keys) {
                unsaved.remove(key)
            }
        } else {
            // Group them together so subsequent writes retry them as an atomic unit
            val sharedGroup = HashMap(batch)
            for (key in batch.keys) {
                unsaved[key] = sharedGroup
            }
            onFailed(list, failureCause)
        }

        request.callback(success)
    }

    /**
     * Waits up to [timeoutMillis] for all currently queued save requests to complete execution.
     *
     * @return `true` if all pending saves finished; `false` if timed out.
     */
    fun flush(timeoutMillis: Long = 5000L): Boolean = runBlocking {
        withTimeoutOrNull(timeoutMillis) {
            val done = Channel<Unit>(1)
            queue.send(SaveRequest(emptyList()) { done.trySend(Unit) })
            done.receive()
            true
        } ?: false
    }

    override fun close() {
        queue.close()
        scope.cancel()
    }
}
