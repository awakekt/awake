/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.sprites.SpriteRenderBatch
import com.awakekt.awake.scene.rendering.RenderContribution
import com.awakekt.awake.scene.rendering.RenderFeature3D
import com.awakekt.awake.scene.rendering.RenderFeatureContext3D
import com.awakekt.awake.scene.scene2d.SortingLayers
import com.awakekt.awake.scene.scene2d.collectSpriteDraws

/** Adapts the 2D scene binding to the existing combined scene pass, placing sprites by the project's [layers]. */
internal class SceneSpriteRenderFeature(
    private val batch: SpriteRenderBatch,
    private val layers: () -> SortingLayers = { SortingLayers.None },
) : RenderFeature3D,
    SceneTextureUploads {
    override fun collect(world: World, context: RenderFeatureContext3D): RenderContribution =
        RenderContribution(draws = batch.collect(world.collectSpriteDraws(layers())))

    override fun forgetTexture(name: String) = batch.forgetTexture(name)

    override fun forgetTextures() = batch.forgetTextures()
}
