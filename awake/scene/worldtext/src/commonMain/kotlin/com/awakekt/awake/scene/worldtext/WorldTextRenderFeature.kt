/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldtext

import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.RenderContribution
import com.awakekt.awake.scene.rendering.RenderFeature3D
import com.awakekt.awake.scene.rendering.RenderFeatureContext3D
import com.awakekt.awake.worldtext.WorldText
import com.awakekt.awake.worldtext.WorldTextBatch

/**
 * Draws every entity's [WorldText] where its [Transform] puts it, through one [WorldTextBatch]:
 * the system that runs world text over a scene. A text whose entity is gone, or no longer has one,
 * is released the frame it is missed. Once warm, a frame that changes no text allocates nothing.
 * [close] it before the renderer is destroyed.
 */
class WorldTextRenderFeature(renderer: Renderer) :
    RenderFeature3D,
    AutoCloseable {
    private val batch = WorldTextBatch(renderer)
    private val draws = ArrayList<RenderDrawCommand>()
    private val contribution = RenderContribution(draws = draws)
    private var live = HashSet<WorldText>()
    private var lastLive = HashSet<WorldText>()

    override fun collect(world: World, context: RenderFeatureContext3D): RenderContribution {
        draws.clear()
        val lens = context.camera.lens
        world.queryEach<Transform, WorldText> { _, transform, text ->
            live.add(text)
            if (text.text.isNotEmpty()) batch.collect(text, transform.worldMatrix, lens, draws)
        }
        for (text in lastLive) if (text !in live) batch.release(text)
        val previous = lastLive
        lastLive = live
        live = previous
        live.clear()
        return contribution
    }

    /** Frees every text's quads and atlas. */
    override fun close() = batch.close()
}
