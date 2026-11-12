/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.feature

import kotlin.reflect.KClass

/**
 * Aggregates installed [GameFeature] instances and manages their message routing and lifecycle dispatch.
 *
 * All lifecycle loops ([ticked], [joined], [left], [ownState]) iterate directly over array/indexed structures to ensure
 * zero allocations during steady-state ticks.
 *
 * @param P The player or session representation.
 * @param M The base message type.
 * @param installedFeatures Initial list of game features to register.
 */
class Features<P, M : Any>(installedFeatures: List<GameFeature<P, M>> = emptyList()) {
    private val featureList = ArrayList(installedFeatures)

    private val featureArray: Array<GameFeature<P, M>> = Array(installedFeatures.size) { installedFeatures[it] }
    private val routers = HashMap<KClass<out M>, (P, M) -> Unit>()

    init {
        // Build combined router and verify uniqueness across features
        for (feature in installedFeatures) {
            val featureRouter = feature.router()
            for (type in featureRouter.handledTypes) {
                require(!routers.containsKey(type)) {
                    "Message type '${type.simpleName}' is registered by multiple features (collides in '${feature.id}')."
                }
                routers[type] = { player, message -> featureRouter.route(player, message) }
            }
        }
    }

    /** The number of installed features. */
    val size: Int get() = featureList.size

    /**
     * Installs [features] into this collection, returning a new [Features] container.
     *
     * @throws IllegalArgumentException if multiple features handle the same message type or have duplicate IDs.
     */
    fun install(vararg features: GameFeature<P, M>): Features<P, M> =
        install(features.asList())

    /**
     * Installs [features] into this collection, returning a new [Features] container.
     *
     * @throws IllegalArgumentException if multiple features handle the same message type or have duplicate IDs.
     */
    fun install(features: Collection<GameFeature<P, M>>): Features<P, M> {
        val merged = ArrayList<GameFeature<P, M>>(featureList.size + features.size)
        merged.addAll(featureList)
        merged.addAll(features)
        return Features(merged)
    }

    /**
     * Returns the first installed feature that is an instance of [type], or `null` when none is.
     *
     * A feature built with [gameFeature] has no type of its own; look up features written as classes.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : GameFeature<P, M>> featureOf(type: KClass<T>): T? {
        val array = featureArray
        for (i in array.indices) {
            val feature = array[i]
            if (type.isInstance(feature)) return feature as T
        }
        return null
    }

    /** Returns the first installed feature of type [T], or `null` when none is. */
    inline fun <reified T : GameFeature<P, M>> featureOf(): T? = featureOf(T::class)

    /** Whether any installed feature handles [message]. */
    fun handles(message: M): Boolean = routers.containsKey(message::class)

    /**
     * Routes [message] from [player] to its owning feature.
     *
     * @return `true` if routed; `false` if unhandled.
     */
    fun route(player: P, message: M): Boolean {
        val handler = routers[message::class] ?: return false
        handler(player, message)
        return true
    }

    /** Dispatches the join event to all installed features in registration order. */
    fun joined(player: P) {
        val array = featureArray
        var i = 0
        val size = array.size
        while (i < size) {
            array[i].onJoin(player)
            i++
        }
    }

    /** Dispatches the leave event to all installed features in registration order. */
    fun left(player: P) {
        val array = featureArray
        var i = 0
        val size = array.size
        while (i < size) {
            array[i].onLeave(player)
            i++
        }
    }

    /** Dispatches the tick event to all installed features with [delta] seconds elapsed. */
    fun ticked(delta: Float) {
        val array = featureArray
        var i = 0
        val size = array.size
        while (i < size) {
            array[i].onTick(delta)
            i++
        }
    }

    /**
     * Dispatches [GameFeature.beforeSnapshot] to all installed features in registration order. The host
     * calls it each tick after [ticked]; call it yourself only when you step features without a host.
     */
    fun beforeSnapshot() {
        val array = featureArray
        var i = 0
        val size = array.size
        while (i < size) {
            array[i].beforeSnapshot()
            i++
        }
    }

    /** Dispatches the own-state generation hook to all installed features. */
    fun ownState(player: P) {
        val array = featureArray
        var i = 0
        val size = array.size
        while (i < size) {
            array[i].ownState(player)
            i++
        }
    }
}
