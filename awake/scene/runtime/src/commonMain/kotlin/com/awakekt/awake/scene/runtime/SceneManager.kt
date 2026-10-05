/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.destroy
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import kotlin.jvm.JvmOverloads

/**
 * Owns the currently-loaded [Scene], if any -- the only thing that may create or destroy one
 * against [world]. A caller that instantiates a [Scene] directly and never registers it here
 * has opted out of safe switching, same as bypassing any other single-owner resource.
 *
 * Tied to one [World] for its whole lifetime by design: a [SceneAppLifecycleRuntime] never reassigns
 * its own `world` after setup, so there is no real case where a single [SceneManager] needs to
 * switch which [World] it targets.
 *
 * @param world The ECS [World] instance this manager operates against.
 * @param componentRegistry Scene components to load with on top of globally registered ones.
 * @param onUnload Callback invoked when a scene is about to be unloaded before entity destruction.
 */
class SceneManager @JvmOverloads constructor(
    private val world: World,
    private val componentRegistry: SceneComponentRegistry? = null,
    private val onUnload: (Scene) -> Unit = {},
) {
    init {
        DefaultSceneComponentResolvers.install()
    }

    /**
     * The currently active instantiated [Scene], or `null` if no scene is loaded.
     */
    var current: Scene? = null
        private set

    /**
     * Tears down whatever is currently loaded (if anything), then instantiates [document].
     *
     * @param document The scene document to instantiate and activate.
     * @return The newly instantiated [Scene] instance.
     */
    fun switchTo(document: SceneDocument): Scene {
        unloadCurrent()
        val scene = SceneLoader.instantiate(document, world, loadRegistry())
        current = scene
        return scene
    }

    /** Globally registered bindings as they are now, plus the caller's. */
    private fun loadRegistry(): SceneComponentRegistry = SceneComponentRegistry().also { registry ->
        componentRegistry?.resolvers?.forEach(registry::register)
    }

    /**
     * Tears down the current scene without loading a replacement, typically during app shutdown.
     */
    fun close() {
        unloadCurrent()
        current = null
    }

    private fun unloadCurrent() {
        val scene = current ?: return
        onUnload(scene)
        scene.destroy()
    }
}
