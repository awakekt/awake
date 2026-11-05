/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlin.reflect.KClass

/**
 * Zero-allocation, O(1) typed message dispatcher that routes incoming messages directly to registered
 * handlers by [KClass] without per-packet allocations or central `when` branches.
 *
 * Build instances with [messageRouter] for fail-fast duplicate-handler checks at registration time,
 * or with [MessageRouter.from] and [MessageRouter.empty].
 */
class MessageRouter<C, M : Any> internal constructor(
    private val handlers: HashMap<KClass<out M>, (C, M) -> Unit>,
    /** Immutable set of message classes handled by this router. */
    val handledTypes: Set<KClass<out M>>,
) {
    /** Number of message types registered in this router. */
    val size: Int get() = handlers.size

    /** Whether this router has a handler registered for the runtime class of [message]. */
    fun handles(message: M): Boolean = handlers.containsKey(message::class)

    /** Whether this router has a handler registered for [messageClass]. */
    fun handles(messageClass: KClass<out M>): Boolean = handlers.containsKey(messageClass)

    /**
     * Routes [message] to its registered handler with [context], returning `true` when a handler
     * was found and executed or `false` when [message]'s runtime class is unhandled.
     */
    fun route(context: C, message: M): Boolean {
        val handler = handlers[message::class] ?: return false
        handler(context, message)
        return true
    }

    internal fun handlerOrNull(messageClass: KClass<out M>): ((C, M) -> Unit)? = handlers[messageClass]

    /** Factory methods for constructing immutable [MessageRouter] instances. */
    companion object {
        /** Creates an immutable [MessageRouter] from a defensive copy of [handlers]. */
        fun <C, M : Any> from(handlers: Map<KClass<out M>, (C, M) -> Unit>): MessageRouter<C, M> {
            if (handlers.isEmpty()) return empty()
            val copy = HashMap<KClass<out M>, (C, M) -> Unit>(handlers.size)
            copy.putAll(handlers)
            return MessageRouter(copy, copy.keys.toSet())
        }

        /** Returns an empty [MessageRouter] that handles no message types. */
        @Suppress("UNCHECKED_CAST")
        fun <C, M : Any> empty(): MessageRouter<C, M> = EMPTY_MESSAGE_ROUTER as MessageRouter<C, M>
    }
}

private val EMPTY_MESSAGE_ROUTER = MessageRouter<Any?, Any>(HashMap(0), emptySet())

/** Marks the [MessageRouterBuilder], [ClientNetFeatureScope], and [ClientNetFeaturesScope] DSL blocks. */
@DslMarker
annotation class NetFeatureDsl

/**
 * Builder scope for registering typed message handlers into a [MessageRouter] via [messageRouter].
 */
@NetFeatureDsl
class MessageRouterBuilder<C, M : Any> internal constructor(
    private val ownerId: String? = null,
) {
    private val handlers = HashMap<KClass<out M>, (C, M) -> Unit>()

    /**
     * Registers a typed handler for [T], throwing [IllegalArgumentException] if [T] is already registered.
     */
    inline fun <reified T : M> on(noinline handler: (context: C, message: T) -> Unit) {
        on(T::class, handler)
    }

    /**
     * Registers a typed handler for [messageClass], throwing [IllegalArgumentException] if [messageClass]
     * is already registered.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : M> on(messageClass: KClass<T>, handler: (context: C, message: T) -> Unit) {
        require(!handlers.containsKey(messageClass)) {
            if (ownerId != null) {
                "Feature '$ownerId' already has a handler registered for message type '${messageClass.simpleName}'."
            } else {
                "MessageRouter already has a handler registered for message type '${messageClass.simpleName}'."
            }
        }
        handlers[messageClass] = { context, message -> handler(context, message as T) }
    }

    internal fun build(): MessageRouter<C, M> = MessageRouter.from(handlers)
}

/**
 * Builds an immutable [MessageRouter] with fail-fast duplicate-handler validation from [configure].
 */
fun <C, M : Any> messageRouter(
    configure: MessageRouterBuilder<C, M>.() -> Unit,
): MessageRouter<C, M> = MessageRouterBuilder<C, M>().apply(configure).build()
