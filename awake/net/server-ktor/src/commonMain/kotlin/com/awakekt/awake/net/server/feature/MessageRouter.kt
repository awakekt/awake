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
 * @param P The player or session representation.
 * @param M The base message type.
 */
@Suppress("OutdatedDocumentation")
class MessageRouter<P, M : Any> private constructor(
    private val handlers: Map<KClass<out M>, (P, M) -> Unit>,
) {
    /** Set of message classes handled by this router. */
    val handledTypes: Set<KClass<out M>> get() = handlers.keys

    /** Whether this router has a handler registered for the runtime class of [message]. */
    fun handles(message: M): Boolean = handlers.containsKey(message::class)

    /** Whether this router has a handler registered for [messageClass]. */
    fun handles(messageClass: KClass<out M>): Boolean = handlers.containsKey(messageClass)

    /**
     * Routes [message] to its registered handler for [player].
     *
     * @return `true` if a handler was found and executed; `false` otherwise.
     */
    fun route(player: P, message: M): Boolean {
        val handler = handlers[message::class] ?: return false
        handler(player, message)
        return true
    }

    companion object {
        /** Creates a [MessageRouter] from an immutable copy of [handlers]. */
        fun <P, M : Any> from(handlers: Map<KClass<out M>, (P, M) -> Unit>): MessageRouter<P, M> =
            MessageRouter(HashMap(handlers))

        /** Creates an empty [MessageRouter]. */
        fun <P, M : Any> empty(): MessageRouter<P, M> = MessageRouter(emptyMap())
    }
}
