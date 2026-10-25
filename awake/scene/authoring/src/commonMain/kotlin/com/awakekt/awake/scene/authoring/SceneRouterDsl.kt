/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.engine.bootstrap.dsl.AppSpecDsl
import com.awakekt.awake.scene.runtime.DeferredSceneRoute
import com.awakekt.awake.scene.runtime.LoadedSceneRoute
import com.awakekt.awake.scene.runtime.SceneAppSpec
import com.awakekt.awake.scene.runtime.SceneRoute
import com.awakekt.awake.scene.runtime.SceneRouteEntry
import com.awakekt.awake.scene.runtime.SceneRouterRuntime
import com.awakekt.awake.scene.runtime.SceneRouterSpec
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Installs a multi-scene router specification into an application specification.
 *
 * @param block Builder lambda configuring scenes and navigation flow.
 */
fun AppSpecDsl.scenes(block: SceneFlowDsl.() -> Unit) {
    install(sceneFlow(block))
}

/**
 * Constructs a [SceneRouterSpec] by evaluating the given scene flow configuration DSL.
 *
 * @param block Builder lambda configuring routes and navigation graph.
 * @return The built [SceneRouterSpec].
 */
fun sceneFlow(block: SceneFlowDsl.() -> Unit): SceneRouterSpec = SceneFlowDsl().apply(block).build()

/**
 * DSL scope for registering scene routes and specifying navigation flow.
 */
class SceneFlowDsl internal constructor() {
    private val routes = mutableListOf<SceneRouteEntry>()
    private var initialRouteId: String? = null

    /**
     * Explicitly sets the initial route ID to display when the router boots.
     *
     * @param id The route identifier of the initial scene.
     */
    fun initial(id: String) {
        initialRouteId = id
    }

    /**
     * Alias for [initial], setting the starting scene route ID.
     *
     * @param id The route identifier of the initial scene.
     */
    fun start(id: String) {
        initial(id)
    }

    /**
     * Registers a scene route backed by a pre-built [SceneAppSpec].
     *
     * @param id Unique route identifier.
     * @param label Human-readable label for navigation UI. Defaults to [id].
     * @param spec Pre-built scene application specification.
     */
    fun route(
        id: String,
        label: String = id,
        spec: SceneAppSpec,
    ) {
        route(SceneRoute(id = id, label = label, spec = spec))
    }

    /**
     * Registers a route built elsewhere: a [SceneRoute], or a [DeferredSceneRoute] whose load returns what
     * to release with its scene. The first route registered is the initial one unless [initial] names
     * another.
     *
     * @param route The route to register; its id must be unique in this router.
     */
    fun route(route: SceneRouteEntry) {
        require(routes.none { it.id == route.id }) { "Scene route '${route.id}' is already registered." }
        routes += route
        if (initialRouteId == null) {
            initialRouteId = route.id
        }
    }

    /**
     * Registers a route whose scene is supplied after the app starts, for instance once the player has
     * signed in: [load] runs off the frame thread the first time the route is entered (or
     * [SceneRouterRuntime.preload]ed), and the scene [loading] declares plays until it returns. See
     * [DeferredSceneRoute] for what happens when the load fails, when the player leaves while it runs,
     * and when the route is entered again.
     *
     * ```kotlin
     * deferredRoute("world", loading = { ui { Text("Loading...") } }) {
     *     val player = signedIn.await() // completed by the sign-in route
     *     sceneApp { name("world") } // with what was read using the player's token
     * }
     * ```
     *
     * @param id Unique route identifier.
     * @param label Human-readable label for navigation UI. Defaults to [id].
     * @param loading Declares the scene shown while [load] runs or after it fails; an empty scene by
     * default. It can read [SceneRouterRuntime.routeStatus] to show progress or an error.
     * @param keepLoaded Whether the scene [load] returned is kept for later entries (the default), or
     * dropped as the route is left so the next entry loads it again.
     * @param loadContext Added to `Dispatchers.Default` for [load]; a test passes a dispatcher of its own.
     * @param load Supplies the route's scene, suspending until it can.
     */
    fun deferredRoute(
        id: String,
        label: String = id,
        loading: SceneAppDsl.() -> Unit = {},
        keepLoaded: Boolean = true,
        loadContext: CoroutineContext = EmptyCoroutineContext,
        load: suspend () -> SceneAppSpec,
    ) {
        route(
            DeferredSceneRoute(
                id = id,
                label = label,
                loading = sceneApp {
                    name("$id-loading")
                    loading()
                },
                keepLoaded = keepLoaded,
                loadContext = loadContext,
                load = { LoadedSceneRoute(load()) },
            ),
        )
    }

    /**
     * Registers a scene route backed by a pre-built [SceneAppSpec]. Alias for [route].
     *
     * @param id Unique route identifier.
     * @param label Human-readable label for navigation UI. Defaults to [id].
     * @param spec Pre-built scene application specification.
     */
    fun scene(
        id: String,
        label: String = id,
        spec: SceneAppSpec,
    ) {
        route(id = id, label = label, spec = spec)
    }

    /**
     * Registers a scene route configured via inline [SceneAppDsl].
     *
     * @param id Unique route identifier.
     * @param label Human-readable label for navigation UI. Defaults to [id].
     * @param block Builder lambda configuring the scene application.
     */
    fun route(
        id: String,
        label: String = id,
        block: SceneAppDsl.() -> Unit,
    ) {
        route(
            id = id,
            label = label,
            spec = sceneApp {
                name(id)
                block()
            },
        )
    }

    /**
     * Registers a scene route configured via inline [SceneAppDsl]. Alias for [route].
     *
     * @param id Unique route identifier.
     * @param label Human-readable label for navigation UI. Defaults to [id].
     * @param block Builder lambda configuring the scene application.
     */
    fun scene(
        id: String,
        label: String = id,
        block: SceneAppDsl.() -> Unit,
    ) {
        route(id = id, label = label, block = block)
    }

    internal fun build(): SceneRouterSpec {
        val initial = checkNotNull(initialRouteId) { "Scene router requires an initial route." }
        return SceneRouterSpec(routes = routes, initialRouteId = initial)
    }
}
