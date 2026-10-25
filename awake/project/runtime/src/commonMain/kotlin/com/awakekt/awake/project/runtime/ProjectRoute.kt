/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.authoring.SceneFlowDsl
import com.awakekt.awake.scene.authoring.sceneApp
import com.awakekt.awake.scene.runtime.DeferredSceneRoute
import com.awakekt.awake.scene.runtime.LoadedSceneRoute
import com.awakekt.awake.scene.runtime.SceneRouteStatus
import com.awakekt.awake.scene.runtime.SceneRouterRuntime
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Registers a route that plays a project supplied after the app starts, such as one whose content is
 * read with a token the player gets only by signing in. [project] runs off the frame thread the first
 * time the route is entered (or [SceneRouterRuntime.preload]ed), and the scene [loading] declares plays
 * until it returns; then [runProject] plays the project, with [touchControls].
 *
 * ```kotlin
 * val signedIn = CompletableDeferred<AssetSource>() // the sign-in route completes it
 * app {
 *     scenes {
 *         route("sign-in") { ... }
 *         projectRoute("world", loading = { ui { Text("Loading...") } }) {
 *             loadProject(signedIn.await(), physicsWorld = ::createJoltPhysicsWorld)
 *         }
 *     }
 * }
 * ```
 *
 * A project plays once, so the route owns the project [project] returns, which must be newly loaded on
 * each call:
 *
 * - **Leaving the route** stops the project's scene and closes the project, its physics world included.
 *   Entering it again calls [project] again.
 * - **Leaving while it loads** lets the load finish. The project waits, unplayed, for the next entry,
 *   which plays it at once; the router never switches to the route on its own. The app stopping cancels
 *   the load, and closes a project that loaded but never played.
 * - **A failed load** ([project] throws) leaves the route on its loading scene with
 *   [SceneRouteStatus.Failed], and logs it. Entering the route again, with
 *   [SceneRouterRuntime.switchTo], calls [project] again.
 *
 * @param id Unique route identifier.
 * @param label Human-readable label for navigation UI. Defaults to [id].
 * @param touchControls Whether the scene's touch-only canvas controls are shown, as for [runProject].
 * @param loading Declares the scene shown while [project] runs or after it fails; an empty scene by
 * default. It can read [SceneRouterRuntime.routeStatus] to show progress or an error.
 * @param loadContext Added to `Dispatchers.Default` for [project]; a test passes a dispatcher of its own.
 * @param project Loads the project, suspending until it can: typically [loadProject] once the player's
 * content can be read.
 */
fun SceneFlowDsl.projectRoute(
    id: String,
    label: String = id,
    touchControls: Boolean = false,
    loading: SceneAppDsl.() -> Unit = {},
    loadContext: CoroutineContext = EmptyCoroutineContext,
    project: suspend () -> LoadedProject,
) {
    route(
        DeferredSceneRoute(
            id = id,
            label = label,
            loading = sceneApp {
                name("$id-loading")
                loading()
            },
            keepLoaded = false,
            loadContext = loadContext,
            load = { playable(project(), touchControls) },
        ),
    )
}

/** The scene that plays [project], which closes it when the router is done with it. */
private fun playable(project: LoadedProject, touchControls: Boolean): LoadedSceneRoute {
    // Nothing would hold a project whose scene could not be built.
    val spec = runCatching { sceneApp { runProject(project, touchControls) } }
        .onFailure { project.close() }
        .getOrThrow()
    return LoadedSceneRoute(spec, release = project::close)
}
