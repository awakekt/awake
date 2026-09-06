/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.rendering.RenderSystem

/** One distance band's worth of an [LodGroup] -- [mesh] draws while the entity is at most
 * [maxDistance] from the camera eye. Typically a decimated mesh per level (see
 * `awake:core:geometry`'s `MeshSimplifier`/`awake:asset:mesh-optimizer`'s CLI for baking
 * these offline), highest detail first. */
data class LodLevel(
    val mesh: Mesh,
    val material: Material,
    val maxDistance: Float,
)

/**
 * A [MeshRenderer]-alternative for an entity with multiple detail levels --
 * [RenderSystem] picks
 * one [LodLevel] per frame by the entity's distance to the primary camera's eye, same "opt-in
 * sibling component, entity picks one shape or the other" relationship [InstancedMeshRenderer]
 * has to [MeshRenderer] -- an entity carries an [LodGroup] instead of a [MeshRenderer], not
 * both.
 *
 * [levels] must be sorted ascending by [LodLevel.maxDistance] -- the first level whose
 * [LodLevel.maxDistance] is at least the entity's actual distance draws; if the entity is
 * farther than every level's threshold, the last (coarsest) level still draws rather than the
 * entity vanishing -- LOD selects detail, it doesn't cull (see [MeshBounds]/frustum culling
 * for that, which composes with this independently).
 */
data class LodGroup(
    val levels: List<LodLevel>,
    /**
     * Width of the band around each threshold, as a fraction of that threshold, in which the
     * level already drawing keeps drawing. Zero restores the bare threshold.
     *
     * A fraction rather than a distance because a threshold at 10m and one at 1000m need very
     * different bands, and one number that scales with each is fewer things to keep in step
     * than one per level.
     */
    val hysteresis: Float = DEFAULT_LOD_HYSTERESIS,
) {
    init {
        require(levels.isNotEmpty()) { "LodGroup needs at least one LodLevel." }
        require(hysteresis >= 0f) { "LodGroup hysteresis is a fraction of a threshold, not an offset: $hysteresis." }
    }

    /**
     * Which level [selectLevel] last returned, or -1 before the first frame.
     *
     * State on the component rather than in a map inside `RenderSystem`, so it lives and dies
     * with the entity: a system rebuilt mid-scene keeps every entity's current level, and a
     * destroyed entity leaves nothing behind to evict. The flip side is that one instance shared
     * by several entities shares its level too -- give each entity its own, as with every other
     * component holding per-entity state.
     */
    var activeLevel: Int = UNSET
        private set

    /**
     * The level to draw at [distance], remembering it for the next call.
     *
     * Without the memory this is a bare threshold, and an entity sitting near one flips level on
     * every frame the camera breathes -- the mesh visibly swaps back and forth while standing
     * still, which reads as flickering geometry rather than as LOD. The band is what stops it:
     * a level that is already drawing keeps drawing until [distance] leaves its threshold by
     * [hysteresis], so crossing costs one switch and coming back costs another.
     *
     * Outside the band the plain threshold decides, which is what makes a teleport land on the
     * right level immediately instead of stepping through the ones in between.
     */
    fun selectLevel(distance: Float): LodLevel {
        val current = activeLevel
        val index = if (current == UNSET || !holds(current, distance)) thresholdIndex(distance) else current
        activeLevel = index
        return levels[index]
    }

    /** The first level whose threshold reaches [distance]; the coarsest when none does -- LOD
     * selects detail, it does not cull. */
    private fun thresholdIndex(distance: Float): Int {
        val index = levels.indexOfFirst { distance <= it.maxDistance }
        return if (index >= 0) index else levels.lastIndex
    }

    /**
     * Whether level [index] should keep drawing at [distance].
     *
     * Its band runs from the previous level's threshold minus [hysteresis] to its own plus
     * [hysteresis]. The coarsest level has no upper edge for the same reason it is the fallback
     * above, and level 0 has no lower one.
     *
     * Open at the bottom and closed at the top, which is what makes a zero [hysteresis] behave
     * exactly like the bare threshold it replaced: at a distance equal to a threshold, the
     * finer level draws.
     */
    private fun holds(index: Int, distance: Float): Boolean {
        val lower = if (index == 0) Float.NEGATIVE_INFINITY else levels[index - 1].maxDistance * (1f - hysteresis)
        val upper = if (index == levels.lastIndex) {
            Float.POSITIVE_INFINITY
        } else {
            levels[index].maxDistance * (1f + hysteresis)
        }
        return distance > lower && distance <= upper
    }

    private companion object {
        const val UNSET = -1
    }
}

/**
 * Ten percent of each threshold: wide enough that the jitter of a nearly-still camera cannot
 * cross it, narrow enough that a level never draws far outside the distance it was authored for.
 */
const val DEFAULT_LOD_HYSTERESIS = 0.1f
