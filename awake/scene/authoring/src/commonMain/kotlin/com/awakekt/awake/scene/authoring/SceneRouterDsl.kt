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

fun AppSpecDsl.scenes(block: SceneFlowDsl.() -> Unit) {
    install(sceneFlow(block))
}

fun sceneFlow(block: SceneFlowDsl.() -> Unit): SceneRouterSpec = SceneFlowDsl().apply(block).build()

class SceneFlowDsl internal constructor() {
    private val routes = mutableListOf<SceneRoute>()
    private var initialRouteId: String? = null

    fun initial(id: String) {
        initialRouteId = id
    }

    fun start(id: String) {
        initial(id)
    }

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

    fun scene(
        id: String,
        label: String = id,
        spec: SceneAppSpec,
    ) {
        route(id = id, label = label, spec = spec)
    }

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
