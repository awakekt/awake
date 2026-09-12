/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.terrain

import com.awakekt.awake.asset.shaderpack.terrainContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.ShaderSet

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
 * Lives in the scene layer rather than the shader pack because it is the only piece that knows
 * about ECS components; the pack takes a plain heightmap and a visibility lambda.
 */
fun terrainContentFeature(shaders: ShaderSet, terrain: TerrainComponent): ContentFeatureSource =
    terrainContentFeature(
        shaders = shaders,
        heightmap = terrain.heightmap,
        config = terrain.clipmapConfig,
        isVisible = { terrain.isVisible },
    )
