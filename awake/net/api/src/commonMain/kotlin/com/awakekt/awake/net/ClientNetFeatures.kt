/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlin.reflect.KClass

/**
 * Aggregates installed [ClientNetFeature] instances and manages their inbound O(1) message routing,
 * outbound request flushing, and connection lifecycle dispatch.
 *
 * All steady-state loops ([flushOutbound], [onConnected], [onDisconnected]) iterate directly over flat
 * arrays in registration order without allocating iterators, and [route] dispatches through a single
 * combined [MessageRouter].
 */
class ClientNetFeatures<C, M : Any>(
    installedFeatures: List<ClientNetFeature<C, M>> = emptyList(),
) {
    private val featureList = ArrayList(installedFeatures)
    private val connectedArray: Array<ClientNetFeature<C, M>>
    private val disconnectedArray: Array<ClientNetFeature<C, M>>
    private val flushArray: Array<ClientNetFeature<C, M>>

    /** Immutable set of IDs of all installed features, in registration order. */
    val featureIds: Set<String>

    /** Combined [MessageRouter] containing all inbound handlers across installed features. */
    val router: MessageRouter<C, M>

    init {
        val ids = LinkedHashSet<String>(installedFeatures.size)
        val handlers = HashMap<KClass<out M>, (C, M) -> Unit>()
        val owners = HashMap<KClass<out M>, String>()
        val connected = ArrayList<ClientNetFeature<C, M>>(installedFeatures.size)
        val disconnected = ArrayList<ClientNetFeature<C, M>>(installedFeatures.size)
        val flush = ArrayList<ClientNetFeature<C, M>>(installedFeatures.size)

        for (feature in installedFeatures) {
            require(feature.id.isNotBlank()) { "Client net feature ID must not be blank." }
            require(ids.add(feature.id)) {
                "Duplicate client net feature ID '${feature.id}'."
            }
            val featureRouter = feature.router()
            for (type in featureRouter.handledTypes) {
                val previousOwner = owners.put(type, feature.id)
                require(previousOwner == null) {
                    "Message type '${type.simpleName}' is already registered by feature '$previousOwner' and cannot also be registered by '${feature.id}'."
                }
                val fallback: (C, M) -> Unit = { context, message ->
                    featureRouter.route(context, message)
                }
                handlers[type] = featureRouter.handlerOrNull(type) ?: fallback
            }
            if (feature !is BuiltClientNetFeature || feature.hasConnectedHandler) {
                connected.add(feature)
            }
            if (feature !is BuiltClientNetFeature || feature.hasDisconnectedHandler) {
                disconnected.add(feature)
            }
            if (feature !is BuiltClientNetFeature || feature.hasFlushOutboundHandler) {
                flush.add(feature)
            }
        }

        featureIds = ids.toSet()
        router = if (handlers.isEmpty()) {
            MessageRouter.empty()
        } else {
            MessageRouter(handlers, handlers.keys.toSet())
        }
        connectedArray = Array(connected.size) { connected[it] }
        disconnectedArray = Array(disconnected.size) { disconnected[it] }
        flushArray = Array(flush.size) { flush[it] }
    }

    /** Number of installed features. */
    val size: Int get() = featureList.size

    /** Immutable set of message classes handled across all installed features. */
    val handledTypes: Set<KClass<out M>> get() = router.handledTypes

    /**
     * Returns a new [ClientNetFeatures] container with [features] appended in order, throwing
     * [IllegalArgumentException] on duplicate feature IDs or colliding message handler types.
     */
    fun install(vararg features: ClientNetFeature<C, M>): ClientNetFeatures<C, M> =
        install(features.asList())

    /**
     * Returns a new [ClientNetFeatures] container with [features] appended in order, throwing
     * [IllegalArgumentException] on duplicate feature IDs or colliding message handler types.
     */
    fun install(features: Collection<ClientNetFeature<C, M>>): ClientNetFeatures<C, M> {
        if (features.isEmpty()) return this
        val merged = ArrayList<ClientNetFeature<C, M>>(featureList.size + features.size)
        merged.addAll(featureList)
        merged.addAll(features)
        return ClientNetFeatures(merged)
    }

    /** Whether any installed feature handles the runtime class of [message]. */
    fun handles(message: M): Boolean = router.handles(message)

    /** Whether any installed feature handles [messageClass]. */
    fun handles(messageClass: KClass<out M>): Boolean = router.handles(messageClass)

    /**
     * Routes [message] with [context] to its owning feature in O(1), returning `true` if handled
     * or `false` if no installed feature handles [message]'s runtime class.
     */
    fun route(context: C, message: M): Boolean = router.route(context, message)

    /** Invokes [ClientNetFeature.flushOutbound] on all installed features in registration order. */
    fun flushOutbound(context: C) {
        val array = flushArray
        var i = 0
        val count = array.size
        while (i < count) {
            array[i].flushOutbound(context)
            i++
        }
    }

    /** Invokes [ClientNetFeature.onConnected] on all installed features in registration order. */
    fun onConnected(context: C) {
        val array = connectedArray
        var i = 0
        val count = array.size
        while (i < count) {
            array[i].onConnected(context)
            i++
        }
    }

    /** Invokes [ClientNetFeature.onDisconnected] on all installed features in registration order. */
    fun onDisconnected(context: C) {
        val array = disconnectedArray
        var i = 0
        val count = array.size
        while (i < count) {
            array[i].onDisconnected(context)
            i++
        }
    }
}

/**
 * Scope for assembling a [ClientNetFeatures] container via the [clientNetFeatures] DSL.
 */
@NetFeatureDsl
class ClientNetFeaturesScope<C, M : Any> internal constructor() {
    private val installed = ArrayList<ClientNetFeature<C, M>>()
    private val seenIds = HashSet<String>()
    private val routeOwners = HashMap<KClass<out M>, String>()

    /**
     * Installs [feature] into this container, throwing [IllegalArgumentException] immediately if its ID
     * or any of its handled message types collides with an already-installed feature.
     */
    fun install(feature: ClientNetFeature<C, M>) {
        require(feature.id.isNotBlank()) { "Client net feature ID must not be blank." }
        require(!seenIds.contains(feature.id)) {
            "Duplicate client net feature ID '${feature.id}'."
        }
        val handled = feature.router().handledTypes
        for (type in handled) {
            val existingOwner = routeOwners[type]
            require(existingOwner == null) {
                "Message type '${type.simpleName}' is already registered by feature '$existingOwner' and cannot also be registered by '${feature.id}'."
            }
        }
        seenIds.add(feature.id)
        for (type in handled) {
            routeOwners[type] = feature.id
        }
        installed.add(feature)
    }

    /**
     * Installs all [features] in order, throwing [IllegalArgumentException] on any duplicate feature ID
     * or colliding message handler type.
     */
    fun install(vararg features: ClientNetFeature<C, M>) {
        for (feature in features) {
            install(feature)
        }
    }

    /**
     * Installs all [features] in iteration order, throwing [IllegalArgumentException] on any duplicate
     * feature ID or colliding message handler type.
     */
    fun install(features: Iterable<ClientNetFeature<C, M>>) {
        for (feature in features) {
            install(feature)
        }
    }

    /**
     * Defines and installs a [ClientNetFeature] with unique identifier [id] configured by [configure].
     */
    fun install(id: String, configure: ClientNetFeatureScope<C, M>.() -> Unit) {
        install(clientNetFeature(id, configure))
    }

    internal fun build(): ClientNetFeatures<C, M> = ClientNetFeatures(installed)
}

/**
 * Builds an immutable [ClientNetFeatures] container from the features installed in [configure].
 */
fun <C, M : Any> clientNetFeatures(
    configure: ClientNetFeaturesScope<C, M>.() -> Unit,
): ClientNetFeatures<C, M> = ClientNetFeaturesScope<C, M>().apply(configure).build()
