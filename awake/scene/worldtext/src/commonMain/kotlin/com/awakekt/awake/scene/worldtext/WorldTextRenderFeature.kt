/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldtext

import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.containsSphere
import com.awakekt.awake.core.math.planes
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.RenderContribution
import com.awakekt.awake.scene.rendering.RenderFeature3D
import com.awakekt.awake.scene.rendering.RenderFeatureContext3D
import com.awakekt.awake.worldtext.WorldText
import com.awakekt.awake.worldtext.WorldTextBatch
import kotlin.math.sqrt

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

    private val center = Vec3f()

    override fun collect(world: World, context: RenderFeatureContext3D): RenderContribution {
        draws.clear()
        val family = world.family<Transform, WorldText>()
        if (family.size == 0) {
            batch.releaseUnseen()
            return contribution
        }
        val lens = context.camera.lens
        // Each view draws only the texts in its frustum: a second view (a canvas portrait) drew every
        // label in the world again, in the transparent pass (#1621). The others are kept, not freed.
        val view = Frustum.planes(lens, context.viewportAspect)
        family.forEach { _, transform, text ->
            if (text.text.isNotEmpty()) {
                if (view.containsSphere(centerOf(transform.worldMatrix), radiusOf(text, transform.worldMatrix))) {
                    batch.collect(text, transform.worldMatrix, lens, draws)
                } else {
                    batch.keep(text)
                }
            }
        }
        batch.releaseUnseen()
        return contribution
    }

    private fun centerOf(placement: Mat4): Vec3f {
        val m = placement.data
        center.x = m[TX]
        center.y = m[TY]
        center.z = m[TZ]
        return center
    }

    /** Frees every text's quads and atlas. */
    override fun close() = batch.close()
}

/**
 * A sphere around [text]'s quads placed by [placement]: half its laid-out diagonal, scaled by the
 * placement's largest axis. Its layout is centred on the placement, as a billboard's is.
 */
private fun radiusOf(text: WorldText, placement: Mat4): Float {
    val layout = text.layout
    val m = placement.data
    val scale = maxOf(axisLength(m[0], m[1], m[2]), axisLength(m[4], m[5], m[6]), axisLength(m[8], m[9], m[10]))
    return sqrt(layout.width * layout.width + layout.height * layout.height) / 2f * scale
}

private fun axisLength(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)

private const val TX = 12
private const val TY = 13
private const val TZ = 14
