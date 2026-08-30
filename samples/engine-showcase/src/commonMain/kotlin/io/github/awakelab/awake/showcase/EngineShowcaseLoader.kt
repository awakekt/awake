/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.scene.runtime.SceneDocument
import io.github.awakelab.awake.scene.runtime.SceneLoader

/** Owns the loaded showcase documents and activates them through the shared scene lifecycle. */
internal class EngineShowcaseLoader {
    private val documents = mutableMapOf<String, SceneDocument>()

    suspend fun preload() {
        preloadEngineShowcases()
        EngineShowcases.forEach { showcase ->
            documents[showcase.id] = SceneLoader.loadFromResource(showcase.scenePath)
        }
    }

    fun activate(id: String, runtime: SceneAppLifecycleRuntime) {
        val showcase = requireNotNull(EngineShowcases.find { it.id == id }) { "Unknown showcase '$id'." }
        val document = requireNotNull(documents[id]) { "Showcase '$id' was not preloaded." }
        // Debug lines belong to whoever drew them last, and the renderer holds the last list it
        // was given rather than clearing per frame. Without this, switching from a navigation
        // showcase to one that draws no lines leaves its blocked markers hanging over the new
        // scene forever. Cleared here rather than in each driver: a driver that draws nothing has
        // no reason to know the previous one did.
        runtime.renderer.drawDebugLines(emptyList())
        runtime.sceneManager.close()
        val instance = runtime.sceneManager.switchTo(document)
        val library = runtime.requireAssetLibrary()
        instance.renderableRequests.forEach { request ->
            runtime.world.add(request.entity, library.resolve(runtime, request))
        }
        showcase.onActivated?.invoke(instance, runtime)
    }

    fun advance(id: String, runtime: SceneAppLifecycleRuntime, delta: Float) {
        EngineShowcases.firstOrNull { it.id == id }?.driver?.invoke(runtime, delta)
    }
}
