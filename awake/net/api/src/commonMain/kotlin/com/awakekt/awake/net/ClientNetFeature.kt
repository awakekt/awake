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

    /**
     * Invoked when the networked entity [entity] leaves this client's view (despawned, or out of
     * interest range), so the feature can drop what it keeps for that entity.
     */
    fun onEntityDisappear(context: C, entity: NetId) = Unit

    /**
     * Invoked when the client clears its replicated world while the session stays up (a zone change,
     * a resync), so the feature can drop all per-world state. A disconnect reports [onDisconnected].
     */
    fun onReset(context: C) = Unit

    /** Invoked on each client network pump pass to drain and send outbound requests queued by this feature. */
    fun flushOutbound(context: C) = Unit

    /** Returns the typed [MessageRouter] containing inbound message handlers owned by this feature. */
    fun router(): MessageRouter<C, M>
}

/** Callback for [ClientNetFeature.onEntityDisappear]; a functional interface so [NetId] is not boxed. */
fun interface ClientEntityDisappearListener<C> {
    /** Invoked when [entity] leaves the client's view. */
    fun onEntityDisappear(context: C, entity: NetId)
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
    private var entityDisappearHandler: ClientEntityDisappearListener<C>? = null
    private var resetHandler: ((C) -> Unit)? = null

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

    /** Sets the hook invoked when a networked entity leaves the client's view. */
    fun onEntityDisappear(action: ClientEntityDisappearListener<C>) {
        entityDisappearHandler = action
    }

    /** Sets the hook invoked when the client clears its replicated world without disconnecting. */
    fun onReset(action: (context: C) -> Unit) {
        resetHandler = action
    }

    /** Builds an immutable [ClientNetFeature] from the handlers configured in this scope. */
    fun build(): ClientNetFeature<C, M> = BuiltClientNetFeature(
        id = id,
        builtRouter = routerBuilder.build(),
        connectedHandler = connectedHandler,
        disconnectedHandler = disconnectedHandler,
        flushOutboundHandler = flushOutboundHandler,
        entityDisappearHandler = entityDisappearHandler,
        resetHandler = resetHandler,
    )
}

internal class BuiltClientNetFeature<C, M : Any>(
    override val id: String,
    private val builtRouter: MessageRouter<C, M>,
    private val connectedHandler: ((C) -> Unit)?,
    private val disconnectedHandler: ((C) -> Unit)?,
    private val flushOutboundHandler: ((C) -> Unit)?,
    private val entityDisappearHandler: ClientEntityDisappearListener<C>? = null,
    private val resetHandler: ((C) -> Unit)? = null,
) : ClientNetFeature<C, M> {
    val hasConnectedHandler: Boolean get() = connectedHandler != null
    val hasDisconnectedHandler: Boolean get() = disconnectedHandler != null
    val hasFlushOutboundHandler: Boolean get() = flushOutboundHandler != null
    val hasEntityDisappearHandler: Boolean get() = entityDisappearHandler != null
    val hasResetHandler: Boolean get() = resetHandler != null

    override fun onConnected(context: C) {
        connectedHandler?.invoke(context)
    }

    override fun onDisconnected(context: C) {
        disconnectedHandler?.invoke(context)
    }

    override fun flushOutbound(context: C) {
        flushOutboundHandler?.invoke(context)
    }

    override fun onEntityDisappear(context: C, entity: NetId) {
        entityDisappearHandler?.onEntityDisappear(context, entity)
    }

    override fun onReset(context: C) {
        resetHandler?.invoke(context)
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
