/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime.session

import com.awakekt.awake.ecs.System
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.error.AppCoroutineScope
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.binding.renderableRequests
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneAppSpec
import com.awakekt.awake.scene.runtime.SceneAssetLibrary
import com.awakekt.awake.scene.runtime.SceneManager
import com.awakekt.awake.scene.runtime.SceneSystemHandle
import com.awakekt.awake.scene.runtime.runPrewarms
import com.awakekt.awake.scene.runtime.schedule.SceneSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The ECS state and schedule for one active scene.
 *
 * Application lifecycle, input routing, and Compose presentation remain outside this type during
 * the compatibility migration.
 *
 * @param spec The specification driving this scene session.
 */
class SceneSession internal constructor(
    private val spec: SceneAppSpec,
) {
    /**
     * The active ECS [World] instance for this session.
     */
    lateinit var world: World
        private set

    /**
     * The [SceneManager] coordinating scene lifecycle and hierarchy loading in this session.
     */
    val sceneManager: SceneManager by lazy {
        SceneManager(world, onUnload = { scene -> assetLibrary?.let { library -> scene.renderableRequests.forEach(library::releaseRenderable) } })
    }

    internal val schedule = SceneSchedule(spec)
    private var assetLibrary: SceneAssetLibrary? = null

    private var scope: CoroutineScope? = null

    /**
     * Where work that lives as long as this session runs: a prewarm and what it decodes off the frame,
     * and a game's own work that should stop with the scene, such as waiting on a server's reply.
     *
     * It is a child of the app's [AppCoroutineScope] when one is registered, so its failures reach the
     * app's error handler and disposing the app stops it, and it is cancelled when this session is
     * disposed. One child's failure stops neither its siblings nor the scope. It runs on
     * [SceneAppSpec.sessionDispatcher], off the frame thread: hand results back to the frame through a
     * channel a system reads, never by changing the [world] from here.
     *
     * Throws until the session is initialized, before its first frame.
     */
    val coroutineScope: CoroutineScope
        get() = checkNotNull(scope) { "The scene session's coroutine scope exists once the session is initialized." }

    // Whether ready began. The dispose hooks undo what the ready hooks did, so a scene whose backend
    // never started, and that never became ready, has nothing for them to undo.
    private var becameReady = false

    internal fun initialize(services: AppServiceLookup) {
        world = World()
        val app = services.service(AppCoroutineScope::class)?.coroutineContext
        scope = CoroutineScope((app ?: EmptyCoroutineContext) + SupervisorJob(app?.get(Job)) + spec.sessionDispatcher)
        assetLibrary = spec.assetLibraryFactory?.invoke()
    }

    internal suspend fun ready(runtime: SceneAppLifecycleRuntime) {
        becameReady = true
        schedule.initialize(runtime)
        spec.scenePopulationBlock(runtime)
        spec.onReadyBlock(runtime)
        schedule.synchronize(world)
    }

    internal fun advance(delta: Float, fixedUpdate: (Float) -> Unit) {
        // Before anything this frame draws, so a texture forgotten from another thread is asked for again whole.
        assetLibrary?.applyRequestedForgets()
        // Before the frame's systems, so a prewarm that became ready lets this frame's spawns draw at once.
        assetLibrary?.runPrewarms()
        schedule.advance(world, delta, fixedUpdate)
    }

    internal fun dispose(runtime: SceneAppLifecycleRuntime) {
        if (becameReady) spec.onDisposeBlock(runtime)
        sceneManager.close()
        assetLibrary?.dispose()
        assetLibrary = null
        scope?.cancel()
        schedule.dispose()
    }

    /**
     * Returns the active [SceneAssetLibrary], throwing [IllegalStateException] if unconfigured.
     *
     * @return The configured [SceneAssetLibrary] instance.
     */
    fun requireAssetLibrary(): SceneAssetLibrary = checkNotNull(assetLibrary) {
        "No scene asset library is registered for '${spec.sceneName ?: "scene"}'."
    }

    /** The scene's asset library, or null when the app registered none. */
    internal fun assetLibraryOrNull(): SceneAssetLibrary? = assetLibrary

    /**
     * Resolves a named [Mesh] from the registered asset library.
     *
     * @param runtime The active [SceneAppLifecycleRuntime] managing the scene.
     * @param name The asset name or identifier of the mesh.
     * @return The loaded [Mesh] instance.
     */
    fun requireMesh(runtime: SceneAppLifecycleRuntime, name: String): Mesh =
        requireAssetLibrary().requireMesh(runtime, name)

    /**
     * Resolves a named [Material] from the registered asset library.
     *
     * @param runtime The active [SceneAppLifecycleRuntime] managing the scene.
     * @param name The asset name or identifier of the material.
     * @return The loaded [Material] instance.
     */
    fun requireMaterial(runtime: SceneAppLifecycleRuntime, name: String): Material =
        requireAssetLibrary().requireMaterial(runtime, name)

    internal fun <T : System> system(handle: SceneSystemHandle<T>): T = schedule.system(handle)

    internal fun system(name: String): System = schedule.system(name)
}
