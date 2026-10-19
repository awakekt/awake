/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.feature

import kotlin.reflect.KClass

/**
 * A discrete game feature that registers handlers for incoming messages and listens to player lifecycle events.
 *
 * Features define their behavior in isolation using [gameFeature], preventing centralized message `when` statements
 * and monolithic server classes.
 *
 * @param P The player or session representation.
 * @param M The base message interface or class handled by the server.
 */
interface GameFeature<P, M : Any> {
    /** Unique identifier for this feature. */
    val id: String

    /** Invoked on the server tick thread when a player enters the active play phase. */
    fun onJoin(player: P) = Unit

    /** Invoked on the server tick thread when a player leaves the active play phase. */
    fun onLeave(player: P) = Unit

    /** Invoked once per server tick on the server tick thread with [delta] seconds elapsed. */
    fun onTick(delta: Float) = Unit

    /** Invoked when generating own-state diffs or synchronization messages for [player]. */
    fun ownState(player: P) = Unit

    /** Returns the typed message router registering handlers owned by this feature. */
    fun router(): MessageRouter<P, M>
}

/**
 * Functional interface for feature tick callbacks to avoid boxing primitive [delta].
 */
fun interface FeatureTickListener {
    fun onTick(delta: Float)
}

/**
 * Scope for constructing a [GameFeature] via the [gameFeature] DSL.
 */
class FeatureScope<P, M : Any>(val id: String) {
    private val handlers = HashMap<KClass<out M>, (P, M) -> Unit>()
    private var joinHandler: ((P) -> Unit)? = null
    private var leaveHandler: ((P) -> Unit)? = null
    private var tickHandler: FeatureTickListener? = null
    private var ownStateHandler: ((P) -> Unit)? = null

    /**
     * Registers a typed message handler for [T].
     *
     * @throws IllegalArgumentException if a handler for [T] is already registered in this feature.
     */
    inline fun <reified T : M> on(noinline handler: (player: P, message: T) -> Unit) {
        on(T::class, handler)
    }

    /**
     * Registers a typed message handler for [messageClass].
     *
     * @throws IllegalArgumentException if a handler for [messageClass] is already registered in this feature.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : M> on(messageClass: KClass<T>, handler: (player: P, message: T) -> Unit) {
        require(!handlers.containsKey(messageClass)) {
            "Feature '$id' already has a handler registered for message type '${messageClass.simpleName}'."
        }
        handlers[messageClass] = { player, message -> handler(player, message as T) }
    }

    /** Sets the hook invoked when a player joins. */
    fun onJoin(action: (player: P) -> Unit) {
        joinHandler = action
    }

    /** Sets the hook invoked when a player leaves. */
    fun onLeave(action: (player: P) -> Unit) {
        leaveHandler = action
    }

    /** Sets the hook invoked once per server tick. */
    fun onTick(action: FeatureTickListener) {
        tickHandler = action
    }

    /** Sets the hook invoked to generate own-state updates for a player. */
    fun ownState(action: (player: P) -> Unit) {
        ownStateHandler = action
    }

    /** Builds the configured [GameFeature]. */
    fun build(): GameFeature<P, M> {
        val builtRouter = MessageRouter.from(handlers)
        val j = joinHandler
        val l = leaveHandler
        val t = tickHandler
        val o = ownStateHandler
        val featureId = id

        return object : GameFeature<P, M> {
            override val id: String = featureId
            override fun onJoin(player: P) { j?.invoke(player) }
            override fun onLeave(player: P) { l?.invoke(player) }
            override fun onTick(delta: Float) { t?.onTick(delta) }
            override fun ownState(player: P) { o?.invoke(player) }
            override fun router(): MessageRouter<P, M> = builtRouter
        }
    }
}

/**
 * Defines a [GameFeature] with identifier [id] using [configure].
 */
fun <P, M : Any> gameFeature(id: String, configure: FeatureScope<P, M>.() -> Unit): GameFeature<P, M> =
    FeatureScope<P, M>(id).apply(configure).build()
