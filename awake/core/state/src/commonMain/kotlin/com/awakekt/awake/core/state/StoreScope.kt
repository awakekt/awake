/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.ContinuationInterceptor
import kotlin.jvm.JvmOverloads

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
 *
 * @property coroutineScope The scope this store owns and cancels on [close].
 */
class DefaultStoreScope private constructor(
    override val coroutineScope: CoroutineScope,
) : StoreScope {

    /**
     * Creates a standalone store scope with no parent.
     *
     * @param dispatcher Coroutine dispatcher used for store background execution.
     */
    // JvmOverloads keeps the no-argument JVM constructor this had as the primary constructor.
    @JvmOverloads
    constructor(
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : this(CoroutineScope(SupervisorJob() + dispatcher))

    /**
     * Creates a store scope that is a child of [parent], as [childStoreScope] does: closing it
     * cancels only this store, and cancelling [parent] cancels it.
     *
     * @param parent The owning scope, normally the app's coroutine scope.
     * @param dispatcher Dispatcher for store work; the parent's dispatcher when null.
     */
    constructor(
        parent: CoroutineScope,
        dispatcher: CoroutineDispatcher? = null,
    ) : this(parent.childStoreScope(dispatcher))

    override fun close() {
        coroutineScope.cancel()
    }
}

/**
 * Creates a coroutine scope for one store that runs as a child of this scope, normally the app's
 * coroutine scope. Pass it as the `scope` of [store], [reducerStore], [MutableStore] and its
 * subclasses.
 *
 * The scope has its own [SupervisorJob] whose parent is this scope's [Job], and it inherits the rest
 * of this scope's context, so a `CoroutineExceptionHandler` there receives the store's uncaught
 * failures. Closing the store cancels only its own job; cancelling this scope cancels every store
 * created from it. A failing store coroutine cancels neither the store, its siblings, nor this scope.
 *
 * ```kotlin
 * val hud = store(HudState(), scope = appScope.childStoreScope())
 * ```
 *
 * Never pass the parent scope itself as a store's `scope`: the store's `close()` would cancel it.
 *
 * @receiver The owning scope.
 * @param dispatcher Dispatcher for store work. When null, this scope's dispatcher, else
 *   [Dispatchers.Default].
 * @return A new scope owned by the store it is given to.
 */
fun CoroutineScope.childStoreScope(dispatcher: CoroutineDispatcher? = null): CoroutineScope {
    val parent = coroutineContext
    val interceptor = dispatcher ?: parent[ContinuationInterceptor] ?: Dispatchers.Default
    return CoroutineScope(parent + SupervisorJob(parent[Job]) + interceptor)
}
