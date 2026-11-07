/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.trail

import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Plane
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.planes
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.RenderContribution
import com.awakekt.awake.scene.rendering.RenderFeature3D
import com.awakekt.awake.scene.rendering.RenderFeatureContext3D
import com.awakekt.awake.trail.TrailBatch
import com.awakekt.awake.trail.TrailRenderer

/**
 * Draws every entity's [TrailRenderer] through its [Transform]'s world matrix, through one
 * [TrailBatch]: the system that hands a scene's trails to the renderer. A trail whose entity is gone,
 * or no longer has one, has its GPU buffer released the frame it is missed. [close] it before the
 * renderer is destroyed.
 *
 * A host with no renderer never builds one, so a headless world allocates nothing for its trails.
 */
class TrailRenderFeature(renderer: Renderer) :
    RenderFeature3D,
    AutoCloseable {
    private val batch = TrailBatch(renderer)
    private val draws = ArrayList<RenderDrawCommand>()
    private val contribution = RenderContribution(draws = draws)

    override fun collect(world: World, context: RenderFeatureContext3D): RenderContribution {
        draws.clear()
        if (!batch.canDraw) return contribution
        val frustum = view.planesFor(context.camera.lens, context.viewportAspect)
        world.queryEach<Transform, TrailRenderer> { _, transform, trail ->
            batch.collect(trail, transform.worldMatrix, frustum, draws)
        }
        batch.releaseUnseen()
        return contribution
    }

    private val view = ViewPlanes()

    /** Frees every trail's buffer. */
    override fun close() = batch.close()
}

/**
 * A view's frustum planes, worked out again only when the view changes, so a still camera allocates
 * nothing. A [Lens] is changed in place, so the view is compared by value.
 */
private class ViewPlanes {
    private var planes: List<Plane> = emptyList()
    private val eye = Vec3f(Float.NaN, 0f, 0f)
    private val center = Vec3f(0f, 0f, 0f)
    private val up = Vec3f(0f, 0f, 0f)
    private val shape = FloatArray(SHAPE_FLOATS)
    private var projection: Lens.Projection? = null

    fun planesFor(lens: Lens, aspect: Float): List<Plane> {
        if (!matches(lens, aspect)) {
            planes = Frustum.planes(lens, aspect)
            eye.set(lens.eye)
            center.set(lens.center)
            up.set(lens.up)
            shape[0] = lens.fovYRadians
            shape[1] = lens.near
            shape[2] = lens.far
            shape[3] = lens.orthoHalfHeight
            shape[4] = aspect
            projection = lens.projection
        }
        return planes
    }

    private fun matches(lens: Lens, aspect: Float): Boolean =
        eye == lens.eye && center == lens.center && up == lens.up && projection == lens.projection &&
            shape[0] == lens.fovYRadians && shape[1] == lens.near && shape[2] == lens.far &&
            shape[3] == lens.orthoHalfHeight && shape[4] == aspect

    private companion object {
        const val SHAPE_FLOATS = 5
    }
}
