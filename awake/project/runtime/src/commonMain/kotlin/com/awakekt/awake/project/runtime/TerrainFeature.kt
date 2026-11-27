/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.kit.terrainlayers.TERRAIN_LAYERS_PROVIDER
import com.awakekt.awake.kit.terrainlayers.TerrainLayersSurfaceProvider
import com.awakekt.awake.kit.terrainlayers.TerrainQuality
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.rendering.terrain.SceneTerrain
import com.awakekt.awake.scene.rendering.terrain.TerrainContentSystem
import com.awakekt.awake.scene.runtime.SceneSystemRole
import com.awakekt.awake.terrain.TerrainSurfaceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/**
 * A scene's `terrain`s, drawn when the host has a renderer: each by [TerrainContentSystem], with the
 * surface its `surface` names from [CoreSceneContent.TerrainSurfaces], or the built-in terrain shading
 * without one. A project's content holds the layered-terrain kit's provider, read from the project's
 * files. When a game sets [CoreSceneContent.TerrainQuality] to [TerrainQuality.Fast], the layered
 * provider and built-in terrain base shaders switch to their lean mobile variants while preserving
 * cross-chunk texture deduplication. A headless host draws none. A terrain's collision is [PhysicsFeature]'s.
 */
internal object TerrainFeature : SceneFeature {
    override val id = "com.awakekt.awake.terrain"

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        if (scene.uses(SceneTerrain::class)) {
            content[CoreSceneContent.TerrainSurfaces] = mapOf(TERRAIN_LAYERS_PROVIDER to TerrainLayersSurfaceProvider(files))
        }
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (!scene.uses(SceneTerrain::class) || !plan.hasRenderer) return
        plan.frame(CoreSystems.TERRAIN, SceneSystemRole.Presentation) { services ->
            val host = services.renderer as? ContentFeatureHost
            // A renderer that can't take content features draws nothing, so it reads nothing either.
            val scope = host?.let { services.backgroundScope("terrain") }
            val quality = services.content[CoreSceneContent.TerrainQuality] ?: TerrainQuality.Standard
            val clipmapConfig = services.content[CoreSceneContent.TerrainClipmapConfig]
            val baseProviders = services.content[CoreSceneContent.TerrainSurfaces].orEmpty()
            val providers = applyTerrainQuality(baseProviders, quality)
            val baseShaders = if (quality == TerrainQuality.Fast) PackShaderSets.TerrainFast else PackShaderSets.Terrain
            TerrainDrawingSystem(host, scope, providers, baseShaders, clipmapConfig)
        }
    }

    private fun applyTerrainQuality(
        providers: Map<String, TerrainSurfaceProvider>,
        quality: TerrainQuality,
    ): Map<String, TerrainSurfaceProvider> {
        if (quality == TerrainQuality.Standard) return providers
        return providers.mapValues { (id, provider) ->
            if (id == TERRAIN_LAYERS_PROVIDER && provider is TerrainLayersSurfaceProvider) {
                provider.withQuality(quality)
            } else {
                provider
            }
        }
    }
}

/**
 * [TerrainContentSystem] through [host], resolving surfaces in [scope], a child of the scene session's
 * scope that this system holds alone. [close] detaches every terrain it drew and cancels what is still
 * resolving. A renderer that cannot attach content features, such as a test renderer, draws nothing
 * and is given no scope.
 */
internal class TerrainDrawingSystem(
    host: ContentFeatureHost?,
    private val scope: CoroutineScope?,
    providers: Map<String, TerrainSurfaceProvider>,
    baseShaders: ShaderSet = PackShaderSets.Terrain,
    clipmapConfigOverride: TerrainClipmapConfig? = null,
) : System,
    AutoCloseable {
    private val terrains = host?.let {
        TerrainContentSystem(
            host = it,
            scope = checkNotNull(scope) { "A terrain that draws resolves in a scope" },
            providers = providers,
            baseShaders = baseShaders,
            surfacedOnly = false,
            clipmapConfigOverride = clipmapConfigOverride,
        )
    }

    override fun update(world: World, delta: Float) {
        terrains?.update(world, delta)
    }

    override fun close() {
        terrains?.detachAll()
        scope?.cancel()
    }
}
