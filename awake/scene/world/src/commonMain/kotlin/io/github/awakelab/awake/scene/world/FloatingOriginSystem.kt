/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.world

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.core.transform.Transform
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Keeps the [StreamObserver] near the scene's local origin by moving the world under it.
 *
 * A `float` carries about seven significant digits, so a position 10km out resolves to roughly a
 * millimetre and one 100km out to a centimetre. The symptom is not a wrong number, it is a
 * *quantised* one: a camera and the mesh in front of it snap to different millimetres each frame,
 * and the frame jitters while every value in the inspector looks correct. Nothing about it
 * improves by being careful with the maths, because the loss is in the storage.
 *
 * So the world moves instead. Once the observer is [threshold] from the local origin, every root
 * transform is translated back by whole [WorldOrigin.quantum] steps, the observer lands near zero
 * again, and [WorldOrigin] records where the local frame now sits. Absolute coordinates are
 * unchanged throughout -- see [WorldOrigin.toAbsolute].
 *
 * ### What this does not move
 *
 * Only `Transform.position` on entities with no parent. Children ride their parent, and shifting
 * them too would double the offset.
 *
 * Everything else holding a world-space coordinate has to be told, through [listeners]:
 *
 * - **Physics.** `PhysicsSystem` reads bodies back into transforms every frame, so the
 *   simulation is authoritative for anything with a `PhysicsBody` and overwrites a shifted
 *   position on the next step. Register `PhysicsOriginShiftListener` (in `:awake:scene:physics`)
 *   and the bodies move too. Without it the scene splits in half: a world that moved and props
 *   that did not. This system cannot wire that itself -- `:awake:scene:scene-core` does not see
 *   the physics module, which is why the seam is a listener rather than a special case.
 * - **Navigation grids, spatial indices, cached paths.** Each holds its own coordinates and each
 *   registers a listener or rebuilds.
 *
 * [WorldPartitionSystem] needs no listener: it reads the origin itself and streams in absolute
 * space, so a shift is invisible to which cells are loaded.
 *
 */
class FloatingOriginSystem(
    /** Distance from the local origin, on any axis, that triggers a shift. Well inside where
     * float precision starts to show, so a shift is a routine event rather than a correction
     * after visible damage. */
    val threshold: Float = DEFAULT_ORIGIN_THRESHOLD,
    /** World units per step, and the granularity of every shift. Snapping to it is what keeps
     * [WorldOrigin] exact and what makes a shift reproducible: the same absolute position always
     * yields the same local one. */
    val quantum: Float = DEFAULT_ORIGIN_QUANTUM,
    /** Notified after each shift, in registration order. See [addListener]. */
    private val listeners: MutableList<OriginShiftListener> = mutableListOf(),
) : System {

    /** Reused across frames: this runs inside the frame loop, where a per-frame allocation is
     * garbage every scene pays for whether or not it ever shifts. */
    private val shift = Vec3f(0f, 0f, 0f)

    /** Registers [listener] for every subsequent shift. */
    fun addListener(listener: OriginShiftListener) {
        listeners += listener
    }

    override fun update(world: World, delta: Float) {
        val observer = observerPosition(world) ?: return
        val stepX = stepsToRecentre(observer.x)
        val stepY = stepsToRecentre(observer.y)
        val stepZ = stepsToRecentre(observer.z)
        if (stepX == 0 && stepY == 0 && stepZ == 0) return

        shift.x = -stepX * quantum
        shift.y = -stepY * quantum
        shift.z = -stepZ * quantum
        world.queryEach<Transform> { _, transform ->
            // Roots only: a child's position is relative to its parent, which has already moved.
            if (transform.parent == null) {
                transform.position.x += shift.x
                transform.position.y += shift.y
                transform.position.z += shift.z
            }
        }

        val origin = originOf(world)
        origin.stepX += stepX
        origin.stepY += stepY
        origin.stepZ += stepZ
        listeners.forEach { it.onOriginShift(shift, origin) }
    }

    /**
     * How many whole steps [coordinate] is from the local origin, or zero while it is within
     * [threshold].
     *
     * Rounded, not truncated, so the observer ends up as near zero as the quantum allows rather
     * than up to a full step out on the far side.
     */
    private fun stepsToRecentre(coordinate: Float): Int =
        if (abs(coordinate) < threshold) 0 else (coordinate / quantum).roundToInt()

    private fun observerPosition(world: World): Vec3f? {
        var found: Vec3f? = null
        // First observer wins, matching WorldPartitionSystem -- two of them would mean two
        // disagreeing ideas of where the world should be centred.
        world.family<Transform, StreamObserver>().forEach { _, transform, _ ->
            if (found == null) found = transform.position
        }
        return found
    }

    /**
     * This scene's origin, created on the first shift.
     *
     * On its own entity rather than in this system, so anything that needs to convert between
     * local and absolute space -- [WorldPartitionSystem], a save file, a consumer's own listener
     * -- reads it from the world it already has instead of from a system it would have to be
     * handed.
     */
    private fun originOf(world: World): WorldOrigin = world.findWorldOrigin() ?: run {
        val origin = WorldOrigin(quantum = quantum)
        world.add(world.create(), origin)
        origin
    }
}

/** Distance from the local origin at which [FloatingOriginSystem] rebases the scene. */
const val DEFAULT_ORIGIN_THRESHOLD = 2048f

/**
 * This scene's [WorldOrigin], or null in a scene that has never shifted.
 *
 * Null rather than a zero default, so a caller can tell "no origin yet" from "origin at zero"
 * when that matters; [WorldOrigin.isZero] answers the second question for callers that only care
 * about the offset.
 */
fun World.findWorldOrigin(): WorldOrigin? {
    var found: WorldOrigin? = null
    queryEach<WorldOrigin> { _, origin ->
        if (found == null) found = origin
    }
    return found
}
