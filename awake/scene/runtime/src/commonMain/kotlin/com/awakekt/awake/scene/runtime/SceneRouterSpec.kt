/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.engine.platform.lifecycle.AppInstaller
import com.awakekt.awake.engine.platform.lifecycle.AppLifecycle
import com.awakekt.awake.render.renderer.Renderer
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Configuration entry binding a scene route identifier and label to a [SceneAppSpec].
 *
 * @property id Unique identifier for this scene route.
 * @property label Human-readable label for navigation menus and diagnostics.
 * @property spec The scene application specification powering this route.
 */
data class SceneRoute(
    val id: String,
    val label: String,
    val spec: SceneAppSpec,
)

/**
 * Lightweight descriptor containing the identifier and label of a scene route.
 *
 * @property id Unique identifier of the scene route.
 * @property label Human-readable display label for the scene route.
 */
data class SceneRouteInfo(
    val id: String,
    val label: String,
)

/**
 * Module installer configuring a multi-scene router within an Awake application.
 *
 * @param routes List of available scene routes registered in this router.
 * @param initialRouteId The identifier of the initial scene to load upon startup.
 */
class SceneRouterSpec(
    routes: List<SceneRoute>,
    private val initialRouteId: String,
) : AppInstaller {
    internal val routes: List<SceneRoute> = routes.toList()

    init {
        require(this.routes.isNotEmpty()) { "Scene router requires at least one route." }
        require(this.routes.any { it.id == initialRouteId }) {
            "Scene router initial route '$initialRouteId' is not registered."
        }
    }

    /**
     * Installs the scene router runtime and lifecycle listeners into the application specification.
     *
     * @param into The application builder receiving router configuration.
     */
    override fun install(into: AppSpecBuilder) {
        val runtime = SceneRouterRuntime(routes, initialRouteId, into.serviceLookup())
        into.service(SceneRouterRuntime::class, runtime)
        into.ready { renderer -> runtime.ready(renderer) }
        into.render { frame -> runtime.update(frame) }
        into.resize { width, height -> runtime.resize(width, height) }
        into.pause { runtime.pause() }
        into.resume { runtime.resume() }
        into.dispose { runtime.dispose() }
    }
}

/**
 * Runtime coordinator managing active scene lifecycle transitions and route navigation.
 *
 * @param routes List of registered scene routes.
 * @param initialRouteId The identifier of the scene to activate on initialization.
 * @param services Application service lookup provider.
 */
class SceneRouterRuntime internal constructor(
    routes: List<SceneRoute>,
    initialRouteId: String,
    private val services: AppServiceLookup,
) : AppLifecycle {
    private val routesById = routes.associateBy(SceneRoute::id)

    /**
     * List of all navigable scene routes known to this router.
     */
    val scenes: List<SceneRouteInfo> = routes.map { SceneRouteInfo(id = it.id, label = it.label) }

    private val initialRoute = routesById.getValue(initialRouteId)
    private var pendingRouteId: String? = null
    private var currentRoute: SceneRoute = initialRoute
    private var currentRuntime: SceneAppLifecycleRuntime? = null
    private var renderer: Renderer? = null

    /**
     * The identifier of the currently active scene route.
     */
    val activeSceneId: String
        get() = currentRoute.id

    /**
     * The human-readable display label of the currently active scene route.
     */
    val activeSceneLabel: String
        get() = currentRoute.label

    /**
     * The [SceneAppLifecycleRuntime] of the currently active scene.
     */
    val sceneRuntime: SceneAppLifecycleRuntime
        get() = checkNotNull(currentRuntime) { "Scene router is not ready yet." }

    override suspend fun ready(renderer: Renderer) {
        this.renderer = renderer
        activate(initialRoute.id)
    }

    override fun update(frame: AppFrame) {
        applyPendingRouteIfNeeded()
        sceneRuntime.update(frame)
    }

    override fun resize(width: Float, height: Float) {
        currentRuntime?.resize(width, height)
    }

    override fun pause() {
        currentRuntime?.pause()
    }

    override fun resume() {
        currentRuntime?.resume()
    }

    override fun dispose() {
        currentRuntime?.dispose()
        currentRuntime = null
        pendingRouteId = null
        renderer = null
    }

    /**
     * Requests a transition to the scene identified by [sceneId].
     *
     * @param sceneId The unique identifier of the destination scene route.
     */
    fun switchTo(sceneId: String) {
        require(routesById.containsKey(sceneId)) { "Scene '$sceneId' is not registered." }
        pendingRouteId = sceneId
    }

    /**
     * Returns descriptor info for the currently active scene route.
     *
     * @return [SceneRouteInfo] for the current scene.
     */
    fun currentScene(): SceneRouteInfo = SceneRouteInfo(activeSceneId, activeSceneLabel)

    private fun applyPendingRouteIfNeeded() {
        val nextRouteId = pendingRouteId ?: return
        pendingRouteId = null
        if (nextRouteId == activeSceneId) return
        activate(nextRouteId)
    }

    private fun activate(sceneId: String) {
        val route = routesById.getValue(sceneId)
        val activeRenderer =
            checkNotNull(renderer) { "Scene router cannot activate '$sceneId' before ready()." }
        currentRuntime?.dispose()
        currentRoute = route
        currentRuntime = SceneAppLifecycleRuntime(route.spec).also { runtime ->
            runtime.initialize(services)
            runImmediateReady {
                runtime.ready(activeRenderer)
            }
        }
    }
}

private fun runImmediateReady(block: suspend () -> Unit) {
    var completed = false
    var failure: Throwable? = null
    block.startCoroutine(
        object : Continuation<Unit> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<Unit>) {
                completed = true
                failure = result.exceptionOrNull()
            }
        },
    )
    check(completed) {
        "Routed scene activation cannot suspend after startup. Keep routed scene ready() work immediate."
    }
    failure?.let { throw it }
}
