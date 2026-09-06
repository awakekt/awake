/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime.session

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneAppSpec
import com.awakekt.awake.scene.runtime.SceneAssetLibrary
import com.awakekt.awake.scene.runtime.SceneManager
import com.awakekt.awake.scene.runtime.SceneSystemHandle
import com.awakekt.awake.scene.runtime.schedule.SceneSchedule

/**
 * The ECS state and schedule for one active scene.
 *
 * Application lifecycle, input routing, and Compose presentation remain outside this type during
 * the compatibility migration.
 */
class SceneSession internal constructor(
    private val spec: SceneAppSpec,
) {
    lateinit var world: World
        private set

    val sceneManager: SceneManager by lazy { SceneManager(world) }

    private val schedule = SceneSchedule(spec)
    private var assetLibrary: SceneAssetLibrary? = null

    internal fun initialize() {
        world = World()
        assetLibrary = spec.assetLibraryFactory?.invoke()
    }

    internal suspend fun ready(runtime: SceneAppLifecycleRuntime) {
        schedule.initialize(runtime)
        spec.scenePopulationBlock(runtime)
        spec.onReadyBlock(runtime)
        schedule.synchronize(world)
    }

    internal fun advance(delta: Float, fixedUpdate: (Float) -> Unit) {
        schedule.advance(world, delta, fixedUpdate)
    }

    internal fun dispose(runtime: SceneAppLifecycleRuntime) {
        spec.onDisposeBlock(runtime)
        sceneManager.close()
        assetLibrary?.dispose()
        assetLibrary = null
        schedule.dispose()
    }

    fun requireAssetLibrary(): SceneAssetLibrary = checkNotNull(assetLibrary) {
        "No scene asset library is registered for '${spec.sceneName ?: "scene"}'."
    }

    fun requireMesh(runtime: SceneAppLifecycleRuntime, name: String): Mesh =
        requireAssetLibrary().requireMesh(runtime, name)

    fun requireMaterial(runtime: SceneAppLifecycleRuntime, name: String): Material =
        requireAssetLibrary().requireMaterial(runtime, name)

    internal fun <T : System> system(handle: SceneSystemHandle<T>): T = schedule.system(handle)

    internal fun system(name: String): System = schedule.system(name)
}
