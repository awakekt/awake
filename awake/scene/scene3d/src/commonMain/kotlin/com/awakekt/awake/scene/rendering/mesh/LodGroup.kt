/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.lod.LodPolicy
import com.awakekt.awake.core.lod.LodSelector
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.rendering.RenderSystem3D

/**
 * One distance band's worth of an [LodGroup] -- [mesh] draws while the entity is at most
 * [maxDistance] from the camera eye. Typically a decimated mesh per level (see
 * `awake:core:geometry`'s `MeshSimplifier`/`awake:asset:mesh-optimizer`'s CLI for baking
 * these offline), highest detail first.
 *
 * @property mesh The mesh rendered at this level of detail.
 * @property material The material used to render the mesh at this level of detail.
 * @property maxDistance The maximum camera distance at which this LOD level is active.
 */
data class LodLevel(
    val mesh: Mesh,
    val material: Material,
    val maxDistance: Float,
)

/**
 * A [MeshRenderer]-alternative for an entity with multiple detail levels --
 * [RenderSystem3D] picks
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
 *
 * **Driving a [MeshRenderer].** An entity that has both draws through its [MeshRenderer], with
 * that renderer's material, factors, culling and flags, and the group only picks which level's
 * [LodLevel.mesh] it draws; the levels' materials are then unused. This is how an imported model
 * gets levels without losing its glTF material, and copies at one level still fold into one
 * instanced draw, since they share that level's mesh and material.
 *
 * **What a game decides at run time.** [policy] pins, clamps or biases the level per entity,
 * [follow] makes a part switch with another model, [minScreenCoverage] switches by size on screen
 * instead of distance, and [SceneLod.quality] is the host's global setting. Each is read every
 * frame, so changing one takes effect on the next.
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
    /** Draws whichever level is showing into the shadow maps, as [MeshRenderer.castsShadows] does for one mesh. */
    val castsShadows: Boolean = true,
) {
    init {
        require(levels.isNotEmpty()) { "LodGroup needs at least one LodLevel." }
        require(hysteresis >= 0f) { "LodGroup hysteresis is a fraction of a threshold, not an offset: $hysteresis." }
    }

    /**
     * Which level [selectLevel] last returned, or -1 before the first frame.
     *
     * State on the component rather than in a map inside `RenderSystem3D`, so it lives and dies
     * with the entity: a system rebuilt mid-scene keeps every entity's current level, and a
     * destroyed entity leaves nothing behind to evict. The flip side is that one instance shared
     * by several entities shares its level too -- give each entity its own, as with every other
     * component holding per-entity state.
     */
    var activeLevel: Int = UNSET
        internal set

    /**
     * Pins, clamps or biases the level this entity draws, and how much coarser its shadows are;
     * see [LodPolicy]. Read every frame, so a game can change it at any time.
     */
    var policy: LodPolicy = LodPolicy.Default

    /**
     * Switch with another model instead of by this one's own thresholds: a part of a composed or
     * skinned character follows its body, so the parts never switch on different frames. Null
     * switches by this group's own thresholds.
     */
    var follow: LodFollow? = null

    /**
     * The smallest screen coverage each level draws at, one per level and descending, to switch by
     * size on screen instead of [LodLevel.maxDistance]: a large model then keeps its detail farther
     * away than a small one. Coverage is the model's bounding-sphere diameter over the viewport
     * height, the measure a glTF asset's `MSFT_screencoverage` uses. Null switches by distance.
     */
    var minScreenCoverage: FloatArray? = null
        set(value) {
            require(value == null || value.size == levels.size) {
                "minScreenCoverage has ${value?.size} values for ${levels.size} levels"
            }
            field = value
        }

    /** The bounding-sphere radius [minScreenCoverage] measures with when the entity has no [MeshBounds]. */
    var boundingRadius: Float = 1f

    /** The level the shadow passes draw: [activeLevel] plus the [policy]'s shadow offset. -1 before the first frame. */
    var shadowLevel: Int = UNSET
        internal set

    /** The level the thresholds alone picked last, which hysteresis holds; [policy] applies on top. */
    internal var thresholdLevel: Int = UNSET

    /** The frame a selection pass last picked this group's level, so a follower can tell its leader is current. */
    internal var selectedFrame: Long = -1L

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
        // The band (see LodSelector.thresholdLevel) runs from the previous level's threshold minus
        // [hysteresis] to its own plus [hysteresis], open at the bottom and closed at the top, so a
        // zero [hysteresis] is the bare threshold: at a distance equal to one, the finer level draws.
        val metric = distance / SceneLod.quality.coverageScale
        thresholdLevel = LodSelector.thresholdLevel(metric, levels.size, thresholdLevel, hysteresis) { levels[it].maxDistance }
        return applyPolicy(thresholdLevel)
    }

    /** The level for a [coverage] of the screen, by [minScreenCoverage], remembering it like [selectLevel]. */
    internal fun selectByCoverage(coverage: Float, minCoverage: FloatArray): LodLevel {
        thresholdLevel = LodSelector.coverageLevel(coverage * SceneLod.quality.coverageScale, minCoverage, thresholdLevel, hysteresis)
        return applyPolicy(thresholdLevel)
    }

    /** Draws [leaderLevel] plus [offset], within this group's [policy]. */
    internal fun selectFollowing(leaderLevel: Int, offset: Int): LodLevel {
        activeLevel = LodSelector.followLevel(leaderLevel, offset, levels.size, policy)
        shadowLevel = LodSelector.shadowLevel(activeLevel, levels.size, policy)
        return levels[activeLevel]
    }

    private fun applyPolicy(level: Int): LodLevel {
        activeLevel = LodSelector.applyPolicy(level, levels.size, policy, SceneLod.quality)
        shadowLevel = LodSelector.shadowLevel(activeLevel, levels.size, policy)
        return levels[activeLevel]
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
