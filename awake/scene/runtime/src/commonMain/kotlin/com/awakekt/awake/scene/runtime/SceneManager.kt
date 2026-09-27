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
 */
class SceneManager @JvmOverloads constructor(
    private val world: World,
    /**
     * Scene components to load with on top of the globally registered ones, such as a kit's
     * (`registerAiBehaviors()`) or a game's own. Its resolvers are added to a registry built on
     * every load, so built-in bindings are present however early this one was created.
     */
    private val componentRegistry: SceneComponentRegistry? = null,
) {
    init {
        DefaultSceneComponentResolvers.install()
    }

    var current: Scene? = null
        private set

    /** Tears down whatever's currently loaded (if anything), then instantiates [document].
     * One call, not a manual teardown-then-load pair -- there is no window where a caller can
     * forget the teardown half. */
    fun switchTo(document: SceneDocument): Scene {
        current?.destroy()
        val scene = SceneLoader.instantiate(document, world, loadRegistry())
        current = scene
        return scene
    }

    /** Globally registered bindings as they are now, plus the caller's. */
    private fun loadRegistry(): SceneComponentRegistry = SceneComponentRegistry().also { registry ->
        componentRegistry?.resolvers?.forEach(registry::register)
    }

    /** Tears down the current scene without loading a replacement -- e.g. app shutdown. */
    fun close() {
        current?.destroy()
        current = null
    }
}
