/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.transform

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.times
import com.awakekt.awake.ecs.ComponentTypeId
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World

/**
 * Propagates world matrices parent-before-child every frame via a memoized DFS (with cycle
 * detection), recomputing only what moved. An entity's matrix is rebuilt when its position,
 * rotation or scale changed since the last frame, when it gained a different parent or [Transform],
 * or when its parent's matrix was rebuilt; otherwise the matrix it already holds is kept. A scene of
 * thousands of static props costs a comparison each, not a matrix product. [Transform.worldVersion]
 * counts the rebuilds, so a consumer can cache what it derives from the matrix.
 *
 * Per-entity state lives in arrays indexed by `entity.id` rather than maps: `Entity` is a value
 * class, so a map key would box it on every visit. A node is "visited this frame" when its stamp
 * equals `frameStamp`, which one increment per [update] invalidates for every entity at once.
 * Reparenting by mutating `Transform.parent` directly is still seen, because the parent is part of
 * what is compared.
 *
 * An entity marked [StaticTransform] is built once and then skipped without reading its values, so
 * it is not even compared. It is built again only for a different or reset [Transform].
 *
 * @param skipsStatic Whether [StaticTransform] entities are skipped once built. An editor that moves
 * them passes false, and every entity is compared each frame.
 */
class TransformSystem(private val skipsStatic: Boolean = true) : System {
    private var visitedStamp = IntArray(0)
    private var visitingStamp = IntArray(0)
    private var frameStamp = 0
    private val localScratch = Mat4()

    /** What each entity's matrix was last built from: its TRS, [Transform], parent, and parent version. */
    private var builtTrs = FloatArray(0)
    private var builtTransform = arrayOfNulls<Transform>(0)
    private var builtMatrix = arrayOfNulls<Mat4>(0)
    private var builtParent = arrayOfNulls<Transform>(0)
    private var builtParentVersion = IntArray(0)

    override fun update(world: World, delta: Float) {
        frameStamp += 1
        val transformType = world.typeId(Transform::class)
        // The world keeps each family up to date and returns the same one each time; this one also
        // holds its transforms, so walking it looks nothing up.
        val statics = world.family<Transform, StaticTransform>()
        if (!skipsStatic || statics.size == 0) {
            world.queryEach<Transform> { entity, transform ->
                propagate(world, transformType, entity, transform)
            }
            return
        }
        statics.forEach { entity, transform, _ ->
            if (!isBuilt(entity.id, transform)) propagate(world, transformType, entity, transform)
        }
        world.family { all(Transform::class).exclude(StaticTransform::class) }.forEach { entity ->
            world.get<Transform>(entity, transformType)?.let { propagate(world, transformType, entity, it) }
        }
    }

    /** Whether [transform] is the one [id]'s matrix was last built from, and has not been reset since. */
    private fun isBuilt(id: Int, transform: Transform): Boolean =
        id < builtTransform.size && builtTransform[id] === transform && !transform.rebuildRequested

    private fun propagate(world: World, transformType: ComponentTypeId, entity: Entity, transform: Transform) {
        val id = entity.id
        ensureCapacity(id)
        if (visitedStamp[id] == frameStamp) return
        check(visitingStamp[id] != frameStamp) { "Transform hierarchy contains a cycle at $entity." }
        visitingStamp[id] = frameStamp

        val parent = transform.parent
        val parentTransform = if (parent != null) world.get<Transform>(parent, transformType) else null
        if (parent != null && parentTransform != null) propagate(world, transformType, parent, parentTransform)
        if (needsRebuild(id, transform, parentTransform)) {
            if (parentTransform != null) {
                transform.computeLocalMatrix(localScratch)
                Mat4.multiplyInPlace(localScratch, parentTransform.worldMatrix, transform.worldMatrix)
            } else {
                transform.computeLocalMatrix(transform.worldMatrix)
            }
            remember(id, transform, parentTransform)
        }
        visitedStamp[id] = frameStamp
    }

    private fun needsRebuild(id: Int, transform: Transform, parent: Transform?): Boolean =
        transform.rebuildRequested ||
            builtTransform[id] !== transform ||
            builtMatrix[id] !== transform.worldMatrix ||
            builtParent[id] !== parent ||
            (parent != null && builtParentVersion[id] != parent.worldVersion) ||
            trsChanged(id, transform)

    private fun trsChanged(id: Int, transform: Transform): Boolean {
        val at = id * TRS_FLOATS
        val trs = builtTrs
        return trs[at] != transform.position.x || trs[at + 1] != transform.position.y || trs[at + 2] != transform.position.z ||
            trs[at + 3] != transform.rotation.x || trs[at + 4] != transform.rotation.y || trs[at + 5] != transform.rotation.z ||
            trs[at + 6] != transform.scale.x || trs[at + 7] != transform.scale.y || trs[at + 8] != transform.scale.z
    }

    private fun remember(id: Int, transform: Transform, parent: Transform?) {
        val at = id * TRS_FLOATS
        builtTrs[at] = transform.position.x
        builtTrs[at + 1] = transform.position.y
        builtTrs[at + 2] = transform.position.z
        builtTrs[at + 3] = transform.rotation.x
        builtTrs[at + 4] = transform.rotation.y
        builtTrs[at + 5] = transform.rotation.z
        builtTrs[at + 6] = transform.scale.x
        builtTrs[at + 7] = transform.scale.y
        builtTrs[at + 8] = transform.scale.z
        builtTransform[id] = transform
        builtMatrix[id] = transform.worldMatrix
        builtParent[id] = parent
        builtParentVersion[id] = parent?.worldVersion ?: 0
        transform.rebuildRequested = false
        transform.worldVersion++
    }

    private fun ensureCapacity(id: Int) {
        if (id < visitedStamp.size) {
            return
        }
        val newSize =
            maxOf(id + 1, maxOf(DEFAULT_CAPACITY, visitedStamp.size * CAPACITY_GROWTH_FACTOR))
        visitedStamp = visitedStamp.copyOf(newSize)
        visitingStamp = visitingStamp.copyOf(newSize)
        builtTrs = builtTrs.copyOf(newSize * TRS_FLOATS)
        builtTransform = builtTransform.copyOf(newSize)
        builtMatrix = builtMatrix.copyOf(newSize)
        builtParent = builtParent.copyOf(newSize)
        builtParentVersion = builtParentVersion.copyOf(newSize)
    }

    private companion object {
        const val DEFAULT_CAPACITY = 16
        const val CAPACITY_GROWTH_FACTOR = 2
        const val TRS_FLOATS = 9
    }
}
