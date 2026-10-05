/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.EmitterPlacement
import com.awakekt.awake.particles.ParticleDrawBuilder
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.camera.Camera

/**
 * Draws the scene's particle emitters with the scene's [Camera]: the whole of what the scene adds to
 * `awake:particles`' [ParticleDrawBuilder], which owns the culling, sorting and packing. A flat
 * emitter lies in the plane of its entity's `Transform`.
 */
internal class SceneParticleCompiler {
    private val builder = ParticleDrawBuilder()

    /**
     * Appends every particle emitter in [world] for [camera], culled with the frustum of the view
     * it is drawn in, [aspect] wide per unit of height. The coordinator delegates the complete
     * particle policy here so it does not know about emitter traversal or the billboard basis.
     */
    fun appendWorldDrawCalls(
        destination: MutableList<RenderDrawCommand>,
        world: World,
        camera: Camera,
        aspect: Float,
    ) {
        builder.appendWorldDrawCalls(destination, world, TransformOrientation, camera.lens, aspect)
    }

    /** Orients an emitter by its entity's `Transform`; drawing needs no position, only the plane. */
    private object TransformOrientation : EmitterPlacement {
        override fun position(world: World, entity: Entity, into: Vec3f): Boolean = false

        override fun orientation(world: World, entity: Entity): Mat4? = world.get<Transform>(entity)?.worldMatrix
    }
}
