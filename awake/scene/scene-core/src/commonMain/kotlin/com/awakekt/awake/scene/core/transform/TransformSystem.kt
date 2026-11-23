/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.transform

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.ecs.ComponentTypeId
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family1
import com.awakekt.awake.ecs.Family2
import com.awakekt.awake.ecs.FamilySpec
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
 * it is not even compared. Its matrix stays as built until it is unmarked, whatever its children do:
 * a child that moves composes with the parent's built matrix and never builds the parent again. It is built again only for a different or reset [Transform]. A frame in
 * which no static joined, left, swapped its [Transform] or was reset does not visit the statics at
 * all, so a still world of marked props costs nothing per prop.
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
    private var builtInheritRotation = BooleanArray(0)

    // The statics family and what it was when this system last walked it, and the packed family of the rest.
    private var statics: Family2<Transform, StaticTransform>? = null
    private var staticChanges = 0
    private var staticResets = 0
    private var moving: Family1<Transform>? = null

    // The tag's raw id, -1 when statics are not skipped: a nullable id would be boxed each frame.
    private var staticType = -1

    override fun update(world: World, delta: Float) {
        frameStamp += 1
        val transformType = world.typeId(TRANSFORM)
        // The world keeps each family up to date and returns the same one each time, until it is
        // cleared; this one also holds its transforms, so walking it looks nothing up.
        val statics = world.family<Transform, StaticTransform>()
        staticType = if (skipsStatic) world.typeId(STATIC).value else -1
        if (!skipsStatic || statics.size == 0) {
            world.queryEach<Transform> { entity, transform ->
                propagate(world, transformType, entity, transform)
            }
            return
        }
        if (staticsMayHaveChanged(statics)) {
            statics.forEach { entity, transform, _ ->
                if (!isBuilt(entity.id, transform)) propagate(world, transformType, entity, transform)
            }
        }
        movingFamily(world, statics).forEach { entity, transform ->
            propagate(world, transformType, entity, transform)
        }
        onMovedStatic?.let { report -> checkStaticSlice(statics, report) }
    }

    /**
     * Called once with each [StaticTransform] entity whose position, rotation or scale was written
     * after it was built: it stays where it was built, which is almost always a mark left on
     * something that moves. Null, the default, checks nothing.
     *
     * The check compares one in [staticCheckSlices] of the statics each frame, a different slice each
     * frame, so it costs a fraction of comparing them all, which would cost what the mark saves. A
     * moved static is reported within [staticCheckSlices] frames of the write.
     */
    var onMovedStatic: ((Entity) -> Unit)? = null

    /** How many frames [onMovedStatic] takes to compare every static once. */
    var staticCheckSlices: Int = DEFAULT_CHECK_SLICES

    private var reported = BooleanArray(0)

    private fun checkStaticSlice(statics: Family2<Transform, StaticTransform>, report: (Entity) -> Unit) {
        val slices = staticCheckSlices.coerceAtLeast(1)
        val slice = frameStamp % slices
        var index = 0
        statics.forEach { entity, transform, _ ->
            if (index++ % slices == slice && movedSinceBuilt(entity.id, transform)) {
                reported[entity.id] = true
                report(entity)
            }
        }
    }

    /** Whether static [id] was built from [transform] and written since, and is not reported yet. */
    private fun movedSinceBuilt(id: Int, transform: Transform): Boolean {
        if (!isBuilt(id, transform) || !trsChanged(id, transform)) return false
        if (id >= reported.size) reported = reported.copyOf(maxOf(id + 1, reported.size * 2, DEFAULT_CAPACITY))
        return !reported[id]
    }

    /** Whether a static may need building: a different family, a member joined, left or swapped, or a transform was reset. */
    private fun staticsMayHaveChanged(family: Family2<Transform, StaticTransform>): Boolean {
        val changed = family !== statics || family.changes != staticChanges || transformResets != staticResets
        staticChanges = family.changes
        staticResets = transformResets
        staticWalks += if (changed) 1 else 0
        return changed
    }

    /** The entities with a [Transform] and no [StaticTransform], kept packed while the world's families are. */
    private fun movingFamily(world: World, family: Family2<Transform, StaticTransform>): Family1<Transform> {
        val kept = moving
        if (kept != null && family === statics) return kept
        statics = family
        return world.family(TRANSFORM, MOVING_FILTER).also { moving = it }
    }

    /** Frames that walked the statics, for the gate that a still world does not. */
    internal var staticWalks = 0
        private set

    /** Whether [transform] is the one [id]'s matrix was last built from, and has not been reset since. */
    private fun isBuilt(id: Int, transform: Transform): Boolean =
        id < builtTransform.size && builtTransform[id] === transform && !transform.rebuildRequested

    private fun propagate(world: World, transformType: ComponentTypeId, entity: Entity, transform: Transform) {
        val id = entity.id
        ensureCapacity(id)
        if (visitedStamp[id] == frameStamp) return
        // A built static is frozen until it is unmarked, whatever its children do: a child that
        // moves composes with the matrix it was built with rather than building it again.
        if (staticType >= 0 && isBuilt(id, transform) && world.has(entity, ComponentTypeId(staticType))) {
            visitedStamp[id] = frameStamp
            return
        }
        check(visitingStamp[id] != frameStamp) { "Transform hierarchy contains a cycle at $entity." }
        visitingStamp[id] = frameStamp

        val parent = transform.parent
        val parentTransform = if (parent != null) world.get<Transform>(parent, transformType) else null
        if (parent != null && parentTransform != null) propagate(world, transformType, parent, parentTransform)
        if (needsRebuild(id, transform, parentTransform)) {
            transform.computeWorldMatrix(parentTransform?.worldMatrix, transform.worldMatrix, localScratch)
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
            builtInheritRotation[id] != transform.inheritRotation ||
            trsChanged(id, transform)

    private fun trsChanged(id: Int, transform: Transform): Boolean {
        val at = id * TRS_FLOATS
        val trs = builtTrs
        return trs[at] != transform.position.x ||
            trs[at + 1] != transform.position.y ||
            trs[at + 2] != transform.position.z ||
            trs[at + 3] != transform.rotation.x ||
            trs[at + 4] != transform.rotation.y ||
            trs[at + 5] != transform.rotation.z ||
            trs[at + 6] != transform.scale.x ||
            trs[at + 7] != transform.scale.y ||
            trs[at + 8] != transform.scale.z
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
        // Stored only when they changed. A moving entity is rebuilt every frame with the same
        // transform, matrix and parent, and an unchanged reference store still pays the garbage
        // collector's write barrier: at tens of thousands of entities that was most of this method.
        if (builtTransform[id] !== transform) builtTransform[id] = transform
        if (builtMatrix[id] !== transform.worldMatrix) builtMatrix[id] = transform.worldMatrix
        if (builtParent[id] !== parent) builtParent[id] = parent
        builtParentVersion[id] = parent?.worldVersion ?: 0
        builtInheritRotation[id] = transform.inheritRotation
        transform.rebuildRequested = false
        transform.worldVersion++
        // Built again, so a later write is reported again.
        if (id < reported.size) reported[id] = false
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
        builtInheritRotation = builtInheritRotation.copyOf(newSize)
    }

    private companion object {
        const val DEFAULT_CAPACITY = 16
        const val CAPACITY_GROWTH_FACTOR = 2
        const val TRS_FLOATS = 9
        const val DEFAULT_CHECK_SLICES = 16

        // Held, not written per frame: a `Transform::class` builds a new class reference each time.
        val TRANSFORM = Transform::class
        val STATIC = StaticTransform::class
        val MOVING_FILTER = FamilySpec.exclude(STATIC)
    }
}
