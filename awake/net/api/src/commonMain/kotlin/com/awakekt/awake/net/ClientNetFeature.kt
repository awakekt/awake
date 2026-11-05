/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlin.reflect.KClass

/**
 * A discrete client-side network feature that owns inbound packet handlers, outbound request flushing,
 * and session connection lifecycle callbacks.
 *
 * Features define their behavior in isolation with [clientNetFeature] and assemble into [ClientNetFeatures],
 * keeping multiplatform clients free of centralized `when` dispatchers and monolithic network systems.
 */
interface ClientNetFeature<C, M : Any> {
    /** Unique identifier for this feature within a [ClientNetFeatures] container. */
    val id: String

    /** Invoked when the client session enters the active connected state. */
    fun onConnected(context: C) = Unit

    /** Invoked when the client session disconnects or resets its session state. */
    fun onDisconnected(context: C) = Unit

    /** Invoked on each client network pump pass to drain and send outbound requests queued by this feature. */
    fun flushOutbound(context: C) = Unit

    /** Returns the typed [MessageRouter] containing inbound message handlers owned by this feature. */
    fun router(): MessageRouter<C, M>
}

/**
 * Scope for constructing a [ClientNetFeature] via the [clientNetFeature] DSL.
 */
@NetFeatureDsl
class ClientNetFeatureScope<C, M : Any>(
    /** Unique identifier of the feature being configured. */
    val id: String,
) {
    private val routerBuilder = MessageRouterBuilder<C, M>(ownerId = id)
    private var connectedHandler: ((C) -> Unit)? = null
    private var disconnectedHandler: ((C) -> Unit)? = null
    private var flushOutboundHandler: ((C) -> Unit)? = null

    init {
        require(id.isNotBlank()) { "Client net feature ID must not be blank." }
    }

    /**
     * Registers a typed inbound message handler for [T], throwing [IllegalArgumentException] if [T]
     * already has a handler in this feature.
     */
    inline fun <reified T : M> on(noinline handler: (context: C, message: T) -> Unit) {
        on(T::class, handler)
    }

    /**
     * Registers a typed inbound message handler for [messageClass], throwing [IllegalArgumentException]
     * if [messageClass] already has a handler in this feature.
     */
    fun <T : M> on(messageClass: KClass<T>, handler: (context: C, message: T) -> Unit) {
        routerBuilder.on(messageClass, handler)
    }

    /** Sets the hook invoked on each outbound pump pass to flush queued requests for this feature. */
    fun flushOutbound(action: (context: C) -> Unit) {
        flushOutboundHandler = action
    }

    /** Sets the hook invoked when the client session enters the connected state. */
    fun onConnected(action: (context: C) -> Unit) {
        connectedHandler = action
    }

    /** Sets the hook invoked when the client session disconnects. */
    fun onDisconnected(action: (context: C) -> Unit) {
        disconnectedHandler = action
    }

    /** Builds an immutable [ClientNetFeature] from the handlers configured in this scope. */
    fun build(): ClientNetFeature<C, M> = BuiltClientNetFeature(
        id = id,
        builtRouter = routerBuilder.build(),
        connectedHandler = connectedHandler,
        disconnectedHandler = disconnectedHandler,
        flushOutboundHandler = flushOutboundHandler,
    )
}

internal class BuiltClientNetFeature<C, M : Any>(
    override val id: String,
    private val builtRouter: MessageRouter<C, M>,
    private val connectedHandler: ((C) -> Unit)?,
    private val disconnectedHandler: ((C) -> Unit)?,
    private val flushOutboundHandler: ((C) -> Unit)?,
) : ClientNetFeature<C, M> {
    val hasConnectedHandler: Boolean get() = connectedHandler != null
    val hasDisconnectedHandler: Boolean get() = disconnectedHandler != null
    val hasFlushOutboundHandler: Boolean get() = flushOutboundHandler != null

    override fun onConnected(context: C) {
        connectedHandler?.invoke(context)
    }

    override fun onDisconnected(context: C) {
        disconnectedHandler?.invoke(context)
    }

    override fun flushOutbound(context: C) {
        flushOutboundHandler?.invoke(context)
    }

    override fun router(): MessageRouter<C, M> = builtRouter
}

/**
 * Defines a [ClientNetFeature] with unique identifier [id] using [configure].
 */
fun <C, M : Any> clientNetFeature(
    id: String,
    configure: ClientNetFeatureScope<C, M>.() -> Unit,
): ClientNetFeature<C, M> = ClientNetFeatureScope<C, M>(id).apply(configure).build()
