/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.ComponentTypeId
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.debug.PhysicsShapeWireframe
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.scene.core.transform.Transform

/**
 * World-space wireframes of what physics is actually colliding against.
 *
 * The shape a body was built with, at the pose the simulation has it in -- which is the whole
 * point, because the collider and the mesh drawn for it are two different things and every bug
 * that matters lives in the gap between them. A capsule sunk into terrain, a heightfield offset by
 * half a tile, a box whose collider never moved with its transform: all of them look like
 * rendering faults until this is switched on.
 *
 * Returns lines rather than drawing them, for the reason `navGridDebugLines` gives:
 * `Renderer.drawDebugLines` replaces the frame's line buffer instead of appending, so a caller
 * with more than one source has to merge them itself or silently keep only the last.
 * [PhysicsDebugSource] draws the same lines through the debug registry, with no list and no
 * allocation per frame.
 *
 * Coloured by motion type rather than by layer. Motion type is what explains a body behaving
 * unexpectedly -- static things that should fall, dynamic things that should not -- and layers are
 * a matrix rather than a colour.
 *
 * A [HeightFieldShape] is drawn as its footprint rather than its samples: a terrain of any size is
 * thousands of quads, and the useful question about a heightfield collider is almost always where
 * its edges are, not what its middle looks like. The footprint honours the field's
 * [HeightFieldShape.origin], so a corner-anchored field is drawn where it collides.
 *
 * Each shape is drawn by [PhysicsShapeWireframe] in `awake:physics:api`, which needs no scene; this
 * walks the world's bodies and places each at its [Transform].
 */
fun physicsDebugLines(world: World): List<LineSegment> {
    val lines = mutableListOf<LineSegment>()
    PhysicsDebugWriter().write(world) { a, b, color -> lines += LineSegment(a.copy(), b.copy(), color) }
    return lines
}

/**
 * Writes every body's collider wireframe as lines: the one writer behind [physicsDebugLines] and
 * [PhysicsDebugSource].
 *
 * The scene half only: it walks the entities with a [Transform] and a [PhysicsBody], turns each
 * transform's Euler rotation into a quaternion and picks the body's colour, and leaves the shape
 * itself to [PhysicsShapeWireframe]. It keeps its own rotation and reuses it for every body, so
 * writing allocates nothing. Frame thread only.
 */
internal class PhysicsDebugWriter {
    private val shapes = PhysicsShapeWireframe()
    private val poseResolver = PhysicsWorldPoseResolver()
    private val position = Vec3f()
    private val rotation = Quat()
    private var transformWorld: World? = null
    private var transformGeneration = -1
    private var transformTypeId = ComponentTypeId(0)

    /** Writes the wireframe of every entity with a [Transform] and a [PhysicsBody] into [out]. */
    fun write(world: World, out: DebugLines) {
        if (transformWorld !== world || transformGeneration != world.generation) {
            transformTypeId = world.typeId(Transform::class)
            transformWorld = world
            transformGeneration = world.generation
        }
        world.queryEach(Transform::class, PhysicsBody::class) { _, transform, body ->
            poseResolver.resolve(world, transform, transformTypeId, position, rotation)
            shapes.write(body.shape, position, rotation, colorOf(body.motionType), out)
        }
    }

    companion object {
        /** The colour a body of [motionType] is drawn in. */
        fun colorOf(motionType: MotionType): Color = when (motionType) {
            MotionType.STATIC -> PhysicsDebugSource.STATIC
            MotionType.DYNAMIC -> PhysicsDebugSource.DYNAMIC
            MotionType.KINEMATIC -> PhysicsDebugSource.KINEMATIC
        }
    }
}
