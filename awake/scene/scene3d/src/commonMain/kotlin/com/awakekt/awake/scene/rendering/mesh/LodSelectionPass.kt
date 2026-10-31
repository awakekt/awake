/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.lod.LodSelector
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.ecs.ComponentStore
import com.awakekt.awake.ecs.Family2
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Picks every [LodGroup]'s level for the frame, before any draw is collected: groups on their own
 * thresholds first, then followers, in rounds, so a follower of a follower still lands on the same
 * frame as the body at the head of the chain. Allocation-free.
 */
internal class LodSelectionPass {
    private var frame = 0L

    fun select(family: Family2<Transform, LodGroup>, groups: ComponentStore<LodGroup>?, boundsStore: ComponentStore<MeshBounds>?, lens: Lens) {
        frame++
        if (family.size == 0) return
        var pending = 0
        family.forEach { entity, transform, group ->
            if (group.follow != null) {
                pending++
            } else {
                selectOwn(group, transform, boundsStore?.get(entity), lens)
            }
        }
        var rounds = 0
        while (pending > 0 && rounds++ < MAX_FOLLOW_DEPTH) pending = followRound(family, groups, boundsStore, lens)
        // A cycle, or a chain deeper than the rounds: those fall back on their own thresholds.
        if (pending > 0) {
            family.forEach { entity, transform, group ->
                if (group.selectedFrame != frame) selectOwn(group, transform, boundsStore?.get(entity), lens)
            }
        }
    }

    /** Picks each follower whose leader is already picked this frame; returns how many still wait. */
    private fun followRound(
        family: Family2<Transform, LodGroup>,
        groups: ComponentStore<LodGroup>?,
        boundsStore: ComponentStore<MeshBounds>?,
        lens: Lens,
    ): Int {
        var pending = 0
        family.forEach { entity, transform, group ->
            val follow = group.follow ?: return@forEach
            if (group.selectedFrame == frame) return@forEach
            val leader = groups?.get(follow.leader)
            when {
                leader == null -> selectOwn(group, transform, boundsStore?.get(entity), lens)
                leader.selectedFrame == frame -> {
                    group.selectFollowing(leader.activeLevel, follow.levelOffset)
                    group.selectedFrame = frame
                }
                else -> pending++
            }
        }
        return pending
    }

    /** Whether [group] was picked this frame, so draw collection reads its level rather than picking one. */
    fun selectedThisFrame(group: LodGroup): Boolean = group.selectedFrame == frame

    private fun selectOwn(group: LodGroup, transform: Transform, bounds: MeshBounds?, lens: Lens) {
        val world = transform.worldMatrix
        val local = bounds?.localBounds
        // The bounds' centre in world space, when there are bounds; the entity's origin otherwise.
        val cx = if (local == null) 0f else (local.min.x + local.max.x) / 2f
        val cy = if (local == null) 0f else (local.min.y + local.max.y) / 2f
        val cz = if (local == null) 0f else (local.min.z + local.max.z) / 2f
        val x = world.m00 * cx + world.m01 * cy + world.m02 * cz + world.m03
        val y = world.m10 * cx + world.m11 * cy + world.m12 * cz + world.m13
        val z = world.m20 * cx + world.m21 * cy + world.m22 * cz + world.m23
        val dx = x - lens.eye.x
        val dy = y - lens.eye.y
        val dz = z - lens.eye.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        val minCoverage = group.minScreenCoverage
        group.selectedFrame = frame
        if (minCoverage == null) {
            group.selectLevel(distance)
            return
        }
        val radius = if (local == null) group.boundingRadius else halfDiagonal(local) * maxScale(transform)
        val coverage = if (lens.projection == Lens.Projection.Orthographic) {
            radius / lens.orthoHalfHeight
        } else {
            LodSelector.screenCoverage(radius, distance, lens.fovYRadians)
        }
        group.selectByCoverage(coverage, minCoverage)
    }

    private fun halfDiagonal(box: Aabb): Float {
        val ex = box.max.x - box.min.x
        val ey = box.max.y - box.min.y
        val ez = box.max.z - box.min.z
        return sqrt(ex * ex + ey * ey + ez * ez) / 2f
    }

    /** The largest axis scale of [transform]'s world matrix, so a scaled-up model keeps its detail longer. */
    private fun maxScale(transform: Transform): Float {
        val m = transform.worldMatrix
        val sx = sqrt(m.m00 * m.m00 + m.m10 * m.m10 + m.m20 * m.m20)
        val sy = sqrt(m.m01 * m.m01 + m.m11 * m.m11 + m.m21 * m.m21)
        val sz = sqrt(m.m02 * m.m02 + m.m12 * m.m12 + m.m22 * m.m22)
        return abs(max(sx, max(sy, sz)))
    }

    private companion object {
        /** Follow chains deeper than this are cycles or mistakes; their remaining links pick on their own. */
        const val MAX_FOLLOW_DEPTH = 8
    }
}
