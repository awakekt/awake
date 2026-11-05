/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.feature

import kotlin.reflect.KClass

/**
 * High-performance, O(1) typed message dispatcher that routes incoming messages directly to registered lambdas
 * without allocation or central `when` branches.
 *
 * Delegates internally to the multiplatform [com.awakekt.awake.net.MessageRouter] in `:awake:net:api` while
 * preserving the server feature binary signature.
 */
class MessageRouter<P, M : Any> private constructor(
    handlers: Map<KClass<out M>, (P, M) -> Unit>,
) {
    private val delegate = com.awakekt.awake.net.MessageRouter.from(handlers)

    /** Set of message classes handled by this router. */
    val handledTypes: Set<KClass<out M>> get() = delegate.handledTypes

    /** Whether this router has a handler registered for the runtime class of [message]. */
    fun handles(message: M): Boolean = delegate.handles(message)

    /** Whether this router has a handler registered for [messageClass]. */
    fun handles(messageClass: KClass<out M>): Boolean = delegate.handles(messageClass)

    /**
     * Routes [message] to its registered handler for [player], returning `true` if a handler was found
     * and executed or `false` otherwise.
     */
    fun route(player: P, message: M): Boolean = delegate.route(player, message)

    /** Factory methods for constructing [MessageRouter] instances. */
    companion object {
        /** Creates a [MessageRouter] from an immutable copy of [handlers]. */
        fun <P, M : Any> from(handlers: Map<KClass<out M>, (P, M) -> Unit>): MessageRouter<P, M> =
            MessageRouter(handlers)

        /** Creates an empty [MessageRouter]. */
        fun <P, M : Any> empty(): MessageRouter<P, M> = MessageRouter(emptyMap())
    }
}
