/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.terrain

import com.awakekt.awake.asset.shaderpack.terrainContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.texture.TextureAsset

/**
 * Renders [terrain], with the component itself as the single source of what gets drawn.
 *
 * The same `TerrainComponent` instance goes into the `World` and into the `RenderPlan`, so its
 * heightmap and ring layout cannot disagree with what the pipeline was built for -- passing a
 * heightmap to the plan separately is exactly how those two drift apart.
 *
 * `isVisible` is read every frame, so toggling it on the component takes effect without
 * rebuilding anything. The rest is not: the heightmap uploads once and the clipmap geometry is
 * built once, both at feature-build time, which is what `ContentFeature` supports and all that
 * load-time terrain data needs. Replacing a live terrain's heightmap has no path here.
 *
 * [origin] is where the heightmap's centre lies in the world; `TerrainContentSystem` passes the
 * entity's `Transform` translation.
 *
 * Lives in the scene layer rather than the shader pack because it is the only piece that knows
 * about ECS components; the pack takes a plain heightmap and a visibility lambda.
 */
fun terrainContentFeature(
    shaders: ShaderSet,
    terrain: TerrainComponent,
    surfaceTextures: Map<Int, TextureAsset> = emptyMap(),
    origin: () -> Vec3f = { AT_ORIGIN },
): ContentFeatureSource =
    terrainContentFeature(
        shaders = shaders,
        heightmap = terrain.heightmap,
        config = terrain.clipmapConfig,
        surfaceTextures = surfaceTextures,
        isVisible = { terrain.isVisible },
        origin = origin,
    )

/** The signature before [terrainContentFeature] took an origin; kept for compiled callers. */
@Deprecated("Kept for binary compatibility.", level = DeprecationLevel.HIDDEN)
fun terrainContentFeature(
    shaders: ShaderSet,
    terrain: TerrainComponent,
    surfaceTextures: Map<Int, TextureAsset> = emptyMap(),
): ContentFeatureSource = terrainContentFeature(shaders, terrain, surfaceTextures, origin = { AT_ORIGIN })

/** Never written: a terrain with no placement of its own reads it. */
private val AT_ORIGIN = Vec3f(0f, 0f, 0f)
