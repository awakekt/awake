/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.EmitterPlacement
import com.awakekt.awake.particles.Particle2DSystem
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Places a 2D emitter's entity by its scene `Transform`, for [Particle2DSystem] (`awake:particles`
 * has no scene of its own): `Particle2DSystem(Transform2DPlacement())`. Use one per system: it
 * keeps a scratch matrix.
 *
 * Once the transform pass has run (or for any child), an entity is where its world matrix put it
 * and turned as that matrix turns about `Z`; before that, a root is at its own `position` and
 * turned by its own `rotation.z`. An entity with no `Transform` has no place, so its emitter
 * simulates around `(0, 0)`.
 */
class Transform2DPlacement : EmitterPlacement {
    private val transformType = Transform::class
    private val rotationScratch = Mat4()

    override fun position(world: World, entity: Entity, into: Vec3f): Boolean {
        val transform = world.get(entity, transformType) ?: return false
        if (transform.usesWorldMatrix()) {
            val m = transform.worldMatrix
            into.set(m.m03, m.m13, m.m23)
        } else {
            into.set(transform.position.x, transform.position.y, transform.position.z)
        }
        return true
    }

    override fun orientation(world: World, entity: Entity): Mat4? {
        val transform = world.get(entity, transformType) ?: return null
        return if (transform.usesWorldMatrix()) {
            transform.worldMatrix
        } else {
            rotationScratch.setEulerTRS(0f, 0f, 0f, 0f, 0f, transform.rotation.z, 1f, 1f, 1f)
        }
    }
}

/** Whether [worldMatrix][Transform.worldMatrix] is current: a child's always is, a root's once the transform pass ran. */
internal fun Transform.usesWorldMatrix(): Boolean = parent != null || worldVersion > 0
