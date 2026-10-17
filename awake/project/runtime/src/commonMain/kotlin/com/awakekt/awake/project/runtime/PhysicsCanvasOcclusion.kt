/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.LineOfSightRays
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.scene.canvas.CanvasFollowOcclusion
import com.awakekt.awake.scene.canvas.CanvasOcclusion
import com.awakekt.awake.scene.canvas.canvasFollowOcclusion
import com.awakekt.awake.scene.canvas.canvasOcclusion
import com.awakekt.awake.scene.canvas.setCanvasOcclusion
import com.awakekt.awake.scene.character.CharacterController
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsSystem

/**
 * Answers the scene's canvas followers' occlusion with physics rays: a follower whose
 * `followRules.occlusion` asks hides while a body stands between the camera and the point it
 * follows. The rays come from a [LineOfSightRays] keyed by the followed node, within the budget of
 * the scene's `canvas_follow_occlusion` ([CanvasFollowOcclusion.Default] without one), read again
 * each frame.
 *
 * The followed node's own body hides nothing: one on the node, on a node above it or under it, or
 * the node's character's own body. Neither the canvas nor physics knows the other; this system,
 * run once a frame before the canvas is drawn, puts itself in the world as its [CanvasOcclusion]
 * and starts each frame's budget.
 *
 * A played project runs it when the scene has a physics world and a follower asks for occlusion or
 * the scene has a `canvas_follow_occlusion`. A host that runs its own systems adds it after its
 * [PhysicsSystem], passing that system, which knows which node each body is for.
 *
 * @param physics The world the rays are cast in.
 * @param bodies The system that built the scene's bodies, for which node a body is.
 */
class PhysicsCanvasOcclusion(physics: PhysicsWorld, private val bodies: PhysicsSystem) :
    System,
    CanvasOcclusion {
    private var world: World? = null
    private val rays = LineOfSightRays<Entity>(physics, ownBody = ::ownBody)
    private val point = Vec3f()

    /** Rays cast this frame so far: at most the budget's `raysPerFrame`. */
    val raysCast: Int get() = rays.raysCast

    override fun update(world: World, delta: Float) {
        if (this.world !== world || world.canvasOcclusion() !== this) {
            this.world = world
            rays.clear()
            world.setCanvasOcclusion(this)
        }
        val settings = world.canvasFollowOcclusion() ?: CanvasFollowOcclusion.Default
        rays.raysPerFrame = settings.raysPerFrame.coerceAtLeast(1)
        rays.reuseFrames = settings.reuseFrames.coerceAtLeast(1)
        rays.onlyLayer = settings.layer?.takeIf { it >= 0 }?.let(::CollisionLayer)
        rays.nextFrame()
    }

    override fun occluded(target: Entity, eye: Vec3f, x: Float, y: Float, z: Float): Boolean {
        if (world == null) return false
        // Copied by the ray it starts, if one is cast; nothing kept, so one point serves every call.
        return rays.blocked(target, eye, point.set(x, y, z))
    }

    /** Whether [body] is [target]'s own: on it, above it or under it, or its character's. */
    private fun ownBody(target: Entity, body: BodyHandle): Boolean {
        val world = world ?: return false
        val owner = bodies.entityFor(body)
        return if (owner != null) {
            world.holds(owner, target) || world.holds(target, owner)
        } else {
            world.characterAbove(target, body)
        }
    }

    /** Whether [above] is [node] or a node above it, through [Transform] parents. */
    private fun World.holds(above: Entity, node: Entity): Boolean {
        var at: Entity? = node
        var steps = 0
        while (at != null && steps++ < MAX_DEPTH) {
            if (at == above) return true
            at = get(at, Transform::class)?.parent
        }
        return false
    }

    /** Whether [body] is the character body of [node] or of a node above it. */
    private fun World.characterAbove(node: Entity, body: BodyHandle): Boolean {
        var at: Entity? = node
        var steps = 0
        while (at != null && steps++ < MAX_DEPTH) {
            if (get(at, CharacterController::class)?.body == body) return true
            at = get(at, Transform::class)?.parent
        }
        return false
    }

    private companion object {
        // A parent link that loops stops here rather than spinning.
        const val MAX_DEPTH = 64
    }
}
