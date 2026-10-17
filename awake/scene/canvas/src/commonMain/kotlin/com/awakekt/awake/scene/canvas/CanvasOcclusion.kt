/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World

/**
 * Whether something stands between the camera and a point a follower follows, for the followers
 * whose `followRules.occlusion` asks: the scene's own answer, which a projector that knows the
 * camera passes [CanvasProjector.occluded] on to. A system that can tell, such as one casting
 * physics rays, puts one in the world with [World.setCanvasOcclusion]; without one, nothing is
 * occluded.
 *
 * It is asked during the frame's canvas, for each follower that asks and is on screen, so it
 * answers from a budget, not with a ray for every one.
 */
fun interface CanvasOcclusion {
    /**
     * Whether something stands between [eye], the camera, and the world point ([x], [y], [z]),
     * which follows [target]. [target]'s own body, around the point, hides nothing.
     */
    fun occluded(target: Entity, eye: Vec3f, x: Float, y: Float, z: Float): Boolean
}

/** The [CanvasOcclusion] set on this world, or null when none is. */
fun World.canvasOcclusion(): CanvasOcclusion? {
    val all = family<CanvasOcclusionHolder>()
    return if (all.size == 0) null else all.componentAt(0).occlusion
}

/**
 * Sets [occlusion] as this world's [CanvasOcclusion], on an entity of its own, in place of any set
 * before; null takes it away.
 */
fun World.setCanvasOcclusion(occlusion: CanvasOcclusion?) {
    val holders = query(CanvasOcclusionHolder::class)
    for (index in 1 until holders.size) destroy(holders[index])
    val holder = holders.firstOrNull()
    when {
        occlusion == null -> holder?.let(::destroy)
        holder == null -> add(create(), CanvasOcclusionHolder(occlusion))
        else -> get(holder, CanvasOcclusionHolder::class)?.occlusion = occlusion
    }
}

/** The component a world's [CanvasOcclusion] sits in: one type, whatever the answer's class. */
private class CanvasOcclusionHolder(var occlusion: CanvasOcclusion)
