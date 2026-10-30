/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.error.AppErrorSource
import com.awakekt.awake.engine.platform.error.reportError
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.engine.platform.lifecycle.AppInstaller
import com.awakekt.awake.engine.platform.lifecycle.AppLifecycle
import com.awakekt.awake.render.renderer.Renderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * A route a [SceneRouterSpec] can enter: a [SceneRoute] whose scene is built before the app starts, or a
 * [DeferredSceneRoute] whose scene loads the first time it is entered.
 */
sealed interface SceneRouteEntry {
    /** Unique identifier of the route, which [SceneRouterRuntime.switchTo] names. */
    val id: String

    /** Human-readable label for navigation menus and diagnostics. */
    val label: String
}

/**
 * Configuration entry binding a scene route identifier and label to a [SceneAppSpec].
 *
 * @property id Unique identifier for this scene route.
 * @property label Human-readable label for navigation menus and diagnostics.
 * @property spec The scene application specification powering this route.
 */
data class SceneRoute(
    override val id: String,
    override val label: String,
    val spec: SceneAppSpec,
) : SceneRouteEntry

/**
 * What a [DeferredSceneRoute] loaded: the [spec] its route plays, and what to give back once the router
 * is done with it.
 *
 * The router closes it, which calls [release] once, when the route drops what it loaded: as its scene is
 * left, for a route that does not keep what it loaded, or when the app stops. A load that finishes after
 * the app stopped is closed at once. The scene's own `onDispose` runs first whenever the scene played.
 *
 * @property spec The scene the route plays.
 * @param release Gives back what the load holds, such as a project and its physics world.
 */
class LoadedSceneRoute(
    val spec: SceneAppSpec,
    private val release: () -> Unit = {},
) : AutoCloseable {
    private var released = false

    /** Calls the release given at construction, once; closing twice is harmless. */
    override fun close() {
        if (released) return
        released = true
        release()
    }
}

/**
 * A route whose scene is supplied after the app starts, such as one that plays content read with a
 * token the player gets only by signing in. The router calls [load] the first time the route is
 * entered (or [SceneRouterRuntime.preload]ed) and shows [loading] meanwhile.
 *
 * - **Loading.** [load] runs off the frame thread, on `Dispatchers.Default` plus [loadContext]. While it
 *   runs, the route is entered: [SceneRouterRuntime.activeSceneId] names it, [loading] plays, and
 *   [SceneRouterRuntime.routeStatus] is [SceneRouteStatus.Loading]. The loaded scene takes the loading
 *   scene's place at the start of the first frame after [load] returns. A route whose [load] has already
 *   finished, or answers without suspending, plays its scene at once, with no loading frame.
 * - **Failure.** When [load] throws, the route stays entered and keeps showing [loading], with
 *   [SceneRouteStatus.Failed] carrying the cause, which is logged once. Entering the route again with
 *   [SceneRouterRuntime.switchTo], from the loading scene or anywhere else, calls [load] again. A
 *   failure is never kept. When the app registered an `AppErrorHandler`, the cause also reaches it
 *   once, as `AppErrorSource.ProjectLoad` with the route id as its subject; its retry loads the route
 *   again, as [SceneRouterRuntime.preload] does.
 * - **Leaving while loading.** Switching to another route leaves the loading scene; [load] keeps going.
 *   What it loads is kept for the next entry, which then plays at once, and the router never switches to
 *   the route on its own when it finishes. The app stopping cancels it.
 * - **Keeping what loaded.** With [keepLoaded], what [load] returned is kept until the app stops, and
 *   every later entry plays its spec again without calling [load], as a [SceneRoute] plays its spec.
 *   Without it, it plays for one entry: leaving the route closes it, and the next entry calls [load]
 *   again. Pass `false` for what can only play once, such as a loaded project.
 *
 * @property id Unique identifier for this scene route.
 * @property label Human-readable label for navigation menus and diagnostics.
 * @property loading The scene shown while [load] runs and after it fails; it can read
 * [SceneRouterRuntime.routeStatus] to show progress or an error.
 * @property keepLoaded Whether what [load] returned is kept for later entries, or closed as the route is left.
 * @property loadContext Added to `Dispatchers.Default` for [load]; a test passes a dispatcher of its own.
 * @property load Supplies the route's scene, suspending until it can, for instance until the player has
 * signed in and the content has been read.
 */
class DeferredSceneRoute(
    override val id: String,
    override val label: String = id,
    val loading: SceneAppSpec,
    val keepLoaded: Boolean = true,
    val loadContext: CoroutineContext = EmptyCoroutineContext,
    val load: suspend () -> LoadedSceneRoute,
) : SceneRouteEntry

/** Where a route's scene is: [SceneRouterRuntime.routeStatus]. A [SceneRoute] is always [Loaded]. */
sealed interface SceneRouteStatus {
    /** A [DeferredSceneRoute] not entered or preloaded yet, or one that played and dropped what it loaded. */
    data object NotLoaded : SceneRouteStatus

    /** A [DeferredSceneRoute]'s load is running. */
    data object Loading : SceneRouteStatus

    /** The route's scene is ready to play, or playing. */
    data object Loaded : SceneRouteStatus

    /**
     * A [DeferredSceneRoute]'s last load threw [cause]. Entering the route again loads it again.
     *
     * @property cause What the load threw.
     */
    data class Failed(val cause: Throwable) : SceneRouteStatus
}

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
 * @param routes List of available scene routes registered in this router: [SceneRoute]s, built before
 * the app starts, and [DeferredSceneRoute]s, loaded when first entered.
 * @param initialRouteId The identifier of the initial scene to load upon startup.
 */
class SceneRouterSpec(
    routes: List<SceneRouteEntry>,
    private val initialRouteId: String,
) : AppInstaller {
    internal val routes: List<SceneRouteEntry> = routes.toList()

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
 * Call it on the frame thread, as the app's UI and systems do. A [DeferredSceneRoute] loads off it and
 * its scene is put in on it, at the start of a frame; see [DeferredSceneRoute] for loading, failure and
 * leaving while loading.
 *
 * @param routes List of registered scene routes.
 * @param initialRouteId The identifier of the scene to activate on initialization.
 * @param services Application service lookup provider.
 */
// TooManyFunctions: six are AppLifecycle's; the rest are the route API and the frame-thread steps of a switch.
@Suppress("TooManyFunctions")
class SceneRouterRuntime internal constructor(
    routes: List<SceneRouteEntry>,
    initialRouteId: String,
    private val services: AppServiceLookup,
) : AppLifecycle {
    private val routesById = routes.associateBy(SceneRouteEntry::id)
    private val deferred = routes.filterIsInstance<DeferredSceneRoute>().associate { it.id to DeferredLoad(it) }

    /**
     * List of all navigable scene routes known to this router.
     */
    val scenes: List<SceneRouteInfo> = routes.map { SceneRouteInfo(id = it.id, label = it.label) }

    private val initialRoute = routesById.getValue(initialRouteId)
    private var pendingRouteId: String? = null
    private var currentRoute: SceneRouteEntry = initialRoute
    private var currentRuntime: SceneAppLifecycleRuntime? = null
    private var renderer: Renderer? = null

    // Deferred routes load here, off the frame thread, and hand what they loaded back through [finished].
    private val loads = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val finished = Channel<FinishedLoad>(Channel.UNLIMITED)

    // Whether the active route is a deferred one showing its loading scene.
    private var showingLoading = false

    // Whether the last scene to go in failed and none replaced it, so the router has no scene to update.
    private var enterFailed = false

    /**
     * The identifier of the currently active scene route, a [DeferredSceneRoute] still loading included.
     */
    val activeSceneId: String
        get() = currentRoute.id

    /**
     * The human-readable display label of the currently active scene route.
     */
    val activeSceneLabel: String
        get() = currentRoute.label

    /**
     * The [SceneAppLifecycleRuntime] of the currently active scene: a [DeferredSceneRoute]'s loading scene
     * until its own has loaded.
     */
    val sceneRuntime: SceneAppLifecycleRuntime
        get() = checkNotNull(currentRuntime) { "Scene router is not ready yet." }

    /**
     * Where the active route's scene is: [SceneRouteStatus.Loading] or [SceneRouteStatus.Failed] while a
     * [DeferredSceneRoute] shows its loading scene, else [SceneRouteStatus.Loaded].
     */
    val activeRouteStatus: SceneRouteStatus
        get() = routeStatus(activeSceneId)

    /**
     * The pointer shape the active scene's UI asks for this frame, or [PointerCursor.Default] before
     * the first scene is ready.
     */
    val cursor: PointerCursor
        get() = currentRuntime?.cursor ?: PointerCursor.Default

    override suspend fun ready(renderer: Renderer) {
        this.renderer = renderer
        activate(initialRoute.id)
    }

    override fun update(frame: AppFrame) {
        collectFinishedLoads()
        applyPendingRouteIfNeeded()
        playLoadedRouteIfWaiting()
        // After a switch that failed with no scene left to play there is nothing to update until a retry.
        if (currentRuntime == null && enterFailed) return
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
        leaveCurrent()
        loads.cancel()
        finished.close()
        while (true) {
            val load = finished.tryReceive().getOrNull() ?: break
            load.result.getOrNull()?.close()
        }
        deferred.values.forEach { state ->
            state.loaded?.close()
            state.loaded = null
            state.status = SceneRouteStatus.NotLoaded
        }
        pendingRouteId = null
        renderer = null
    }

    /**
     * Requests a transition to the scene identified by [sceneId], made at the start of the next frame.
     * Naming a [DeferredSceneRoute] whose last load failed loads it again, even while it is the active
     * route.
     *
     * @param sceneId The unique identifier of the destination scene route.
     */
    fun switchTo(sceneId: String) {
        require(routesById.containsKey(sceneId)) { "Scene '$sceneId' is not registered." }
        pendingRouteId = sceneId
    }

    /**
     * Starts loading the [DeferredSceneRoute] [sceneId] without entering it, for instance as soon as the
     * player has signed in, so that entering it later plays it at once. Does nothing for a [SceneRoute],
     * or for a deferred route that is loading or loaded already; one whose last load failed loads again.
     *
     * @param sceneId The unique identifier of the route to load.
     */
    fun preload(sceneId: String) {
        require(routesById.containsKey(sceneId)) { "Scene '$sceneId' is not registered." }
        deferred[sceneId]?.let(::startLoad)
    }

    /**
     * Where the route [sceneId]'s scene is. A [SceneRoute] is always [SceneRouteStatus.Loaded].
     *
     * @param sceneId The unique identifier of the route.
     * @return The route's [SceneRouteStatus].
     */
    fun routeStatus(sceneId: String): SceneRouteStatus {
        require(routesById.containsKey(sceneId)) { "Scene '$sceneId' is not registered." }
        return deferred[sceneId]?.status ?: SceneRouteStatus.Loaded
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
        if (nextRouteId == activeSceneId && currentRuntime != null) {
            // Entering the active route again retries a failed load; anything else stays as it is.
            deferred[nextRouteId]?.let(::startLoad)
            return
        }
        activate(nextRouteId)
    }

    private fun playLoadedRouteIfWaiting() {
        if (!showingLoading) return
        // A retry started this frame may have answered without suspending.
        collectFinishedLoads()
        if (deferred[activeSceneId]?.status == SceneRouteStatus.Loaded) activate(activeSceneId)
    }

    /**
     * Makes [sceneId] the active route: the active scene is disposed first, then the route's scene is put in.
     *
     * A route whose scene fails to go in leaves the app with the old scene already gone, so the failure
     * is not left to end the frame. It reaches the app's `AppErrorHandler` once as
     * `AppErrorSource.SceneSwitch`, with the route id as its subject and a retry that enters the route
     * again, and the half-built scene is disposed. The route that was active is then entered again, so
     * the app has a scene to show the error over; only when that fails too, or when the failing route
     * was the only one entered, does the router idle, updating nothing, until the retry or another
     * [switchTo]. With no handler registered the exception is thrown to the caller, as before. A first
     * activation, from [ready], is not contained here: a failure to start is the app's `Bootstrap`.
     */
    // TooGenericExceptionCaught: whatever a route's scene throws as it goes in must not leave the app with no scene and no report.
    @Suppress("TooGenericExceptionCaught")
    private fun activate(sceneId: String) {
        val previousId = if (currentRuntime != null) currentRoute.id else null
        leaveCurrent()
        try {
            enter(sceneId)
        } catch (failure: Exception) {
            if (previousId == null && !enterFailed) throw failure
            enterFailed = true
            showingLoading = false
            if (!services.reportError(AppErrorSource.SceneSwitch, failure, retry = { retryEntering(sceneId) }, subject = sceneId)) throw failure
            log.error(failure) { "Can't enter scene route '$sceneId': ${failure.message}" }
            if (previousId != null && previousId != sceneId) reenter(previousId)
        }
    }

    /** Puts the scene of [sceneId] in as the active one, or throws having disposed what it began. */
    // TooGenericExceptionCaught: whatever the scene throws as it goes in is rethrown, after the half-built scene is disposed.
    @Suppress("TooGenericExceptionCaught")
    private fun enter(sceneId: String) {
        val route = routesById.getValue(sceneId)
        val activeRenderer =
            checkNotNull(renderer) { "Scene router cannot activate '$sceneId' before ready()." }
        currentRoute = route
        val spec = when (route) {
            is SceneRoute -> route.spec
            is DeferredSceneRoute -> {
                val state = deferred.getValue(route.id)
                startLoad(state)
                // A load that answered without suspending is played now rather than after a loading frame.
                collectFinishedLoads()
                val loaded = state.loaded
                showingLoading = loaded == null
                loaded?.spec ?: route.loading
            }
        }
        val runtime = SceneAppLifecycleRuntime(spec)
        try {
            runtime.initialize(services)
            runImmediateReady {
                runtime.ready(activeRenderer)
            }
        } catch (failure: Exception) {
            disposeQuietly(runtime)
            throw failure
        }
        currentRuntime = runtime
        enterFailed = false
    }

    /** Puts the route that was active back after the one that replaced it failed; its own failure is reported, not retried. */
    // TooGenericExceptionCaught: as in activate.
    @Suppress("TooGenericExceptionCaught")
    private fun reenter(previousId: String) {
        try {
            enter(previousId)
        } catch (failure: Exception) {
            showingLoading = false
            services.reportError(AppErrorSource.SceneSwitch, failure, retry = { retryEntering(previousId) }, subject = previousId)
            log.error(failure) { "Can't enter scene route '$previousId' again: ${failure.message}" }
        }
    }

    /** What a scene switch's retry does: asks for [sceneId] at the start of the next frame, even from idle. */
    private fun retryEntering(sceneId: String) {
        pendingRouteId = sceneId
    }

    // TooGenericExceptionCaught: a scene half built has nothing sound to dispose, and the failure that matters is the one that left it so.
    @Suppress("TooGenericExceptionCaught")
    private fun disposeQuietly(runtime: SceneAppLifecycleRuntime) {
        try {
            runtime.dispose()
        } catch (failure: Exception) {
            log.error(failure) { "Can't dispose a scene that failed to start: ${failure.message}" }
        }
    }

    /** Disposes the active scene and, for a route that keeps nothing, closes what it loaded. */
    private fun leaveCurrent() {
        val runtime = currentRuntime ?: return
        runtime.dispose()
        currentRuntime = null
        val state = deferred[currentRoute.id]
        if (state != null && !showingLoading && !state.route.keepLoaded) {
            state.loaded?.close()
            state.loaded = null
            state.status = SceneRouteStatus.NotLoaded
        }
        showingLoading = false
    }

    /** Starts [state]'s load unless it is loading or loaded already. */
    private fun startLoad(state: DeferredLoad) {
        if (state.status == SceneRouteStatus.Loading || state.status == SceneRouteStatus.Loaded) return
        val route = state.route
        val attempt = ++state.attempt
        state.status = SceneRouteStatus.Loading
        loads.launch(route.loadContext) {
            val result = loadCatching(route)
            // A load that finishes as the app stops has no router left to play it.
            if (finished.trySend(FinishedLoad(route.id, attempt, result)).isFailure) result.getOrNull()?.close()
        }
    }

    /** On the frame thread: records every load that has finished since the last call. */
    private fun collectFinishedLoads() {
        while (true) {
            val load = finished.tryReceive().getOrNull() ?: return
            val state = deferred.getValue(load.routeId)
            if (load.attempt != state.attempt || state.status != SceneRouteStatus.Loading) {
                load.result.getOrNull()?.close()
                continue
            }
            load.result.fold(
                onSuccess = { loaded ->
                    state.loaded = loaded
                    state.status = SceneRouteStatus.Loaded
                },
                onFailure = { failure ->
                    log.error(failure) { "Can't load scene route '${load.routeId}': ${failure.message}" }
                    state.status = SceneRouteStatus.Failed(failure)
                    // After the status, so a handler that reads it sees the failure. Retrying loads the route
                    // again without entering it: if it is still the active route, its scene then plays.
                    services.reportError(AppErrorSource.ProjectLoad, failure, retry = { preload(load.routeId) }, subject = load.routeId)
                },
            )
        }
    }

    /** A [DeferredSceneRoute]'s load, as the frame thread sees it. Read and written on the frame thread only. */
    private class DeferredLoad(val route: DeferredSceneRoute) {
        var status: SceneRouteStatus = SceneRouteStatus.NotLoaded
        var loaded: LoadedSceneRoute? = null
        var attempt = 0
    }

    private class FinishedLoad(val routeId: String, val attempt: Int, val result: Result<LoadedSceneRoute>)
}

/** Runs [route]'s load, answering what it threw as a failure, unless the load was cancelled. */
// TooGenericExceptionCaught: whatever a game's loader throws must leave the route failed, not the frame loop.
@Suppress("TooGenericExceptionCaught")
private suspend fun loadCatching(route: DeferredSceneRoute): Result<LoadedSceneRoute> = try {
    Result.success(route.load())
} catch (failure: Exception) {
    currentCoroutineContext().ensureActive()
    Result.failure(failure)
}

private val log = Logger("scene-router")

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
