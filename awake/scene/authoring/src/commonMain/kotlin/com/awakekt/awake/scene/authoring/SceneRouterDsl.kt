/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.engine.bootstrap.dsl.AppSpecDsl
import com.awakekt.awake.scene.runtime.SceneAppSpec
import com.awakekt.awake.scene.runtime.SceneRoute
import com.awakekt.awake.scene.runtime.SceneRouterSpec

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
    private val routes = mutableListOf<SceneRoute>()
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
        require(routes.none { it.id == id }) { "Scene route '$id' is already registered." }
        routes += SceneRoute(id = id, label = label, spec = spec)
        if (initialRouteId == null) {
            initialRouteId = id
        }
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
