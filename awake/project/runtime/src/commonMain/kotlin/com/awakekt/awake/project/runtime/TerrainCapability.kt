/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.kit.terrainlayers.TERRAIN_LAYERS_PROVIDER
import com.awakekt.awake.kit.terrainlayers.TerrainLayersSurfaceProvider
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.rendering.terrain.SceneTerrain
import com.awakekt.awake.scene.rendering.terrain.TerrainContentSystem
import com.awakekt.awake.terrain.TerrainSurfaceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * A scene's `terrain`s, drawn when the host has a renderer: each by [TerrainContentSystem], with the
 * surface its `surface` names from [CoreSceneContent.TerrainSurfaces], or the built-in terrain shading
 * without one. A project's content holds the layered-terrain kit's provider, read from the project's
 * files. A headless host draws none. A terrain's collision is [PhysicsCapability]'s.
 */
internal object TerrainCapability : SceneCapability {
    override val id = "com.awakekt.awake.terrain"

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        if (scene.uses(SceneTerrain::class)) {
            content[CoreSceneContent.TerrainSurfaces] = mapOf(TERRAIN_LAYERS_PROVIDER to TerrainLayersSurfaceProvider(files))
        }
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (!scene.uses(SceneTerrain::class) || !plan.hasRenderer) return
        plan.frame("terrain") { services ->
            TerrainDrawingSystem(services.renderer as? ContentFeatureHost, services.content[CoreSceneContent.TerrainSurfaces].orEmpty())
        }
    }
}

/**
 * [TerrainContentSystem] through [host], with a scope of its own for resolving surfaces. [close]
 * detaches every terrain it drew and cancels what is still resolving. A renderer that cannot attach
 * content features, such as a test renderer, draws nothing.
 */
internal class TerrainDrawingSystem(
    host: ContentFeatureHost?,
    providers: Map<String, TerrainSurfaceProvider>,
) : System, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val terrains = host?.let { TerrainContentSystem(it, scope, providers) }

    override fun update(world: World, delta: Float) {
        terrains?.update(world, delta)
    }

    override fun close() {
        terrains?.detachAll()
        scope.cancel()
    }
}
