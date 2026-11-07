/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

/**
 * An outline on the ground plane, in world X and Z: what an obstacle covers or where an area lies.
 *
 * Two shapes, because they are what static colliders reduce to on a grid: a box's footprint and a
 * cylinder's (or a sphere's, or a capsule's standing upright). A rotated box is passed as its
 * world-space bounds, which errs on the blocked side.
 */
sealed class NavFootprint {
    /** Smallest world X the footprint reaches. */
    abstract val minX: Float

    /** Smallest world Z the footprint reaches. */
    abstract val minZ: Float

    /** Largest world X the footprint reaches. */
    abstract val maxX: Float

    /** Largest world Z the footprint reaches. */
    abstract val maxZ: Float

    /**
     * Whether world ([x], [z]) lies inside the outline, edges included.
     *
     * @param x World X.
     * @param z World Z.
     * @return True inside or on the edge.
     */
    abstract fun contains(x: Float, z: Float): Boolean

    /**
     * Whether the outline touches the square of half-width [half] centred on ([x], [z]): a sample's
     * cell. The bake tests cells rather than centres, so a wall thinner than a sample still blocks.
     */
    internal abstract fun overlapsCell(x: Float, z: Float, half: Float): Boolean

    /**
     * An axis-aligned rectangle.
     *
     * @property minX Smallest world X.
     * @property minZ Smallest world Z.
     * @property maxX Largest world X.
     * @property maxZ Largest world Z.
     */
    class Rect(
        override val minX: Float,
        override val minZ: Float,
        override val maxX: Float,
        override val maxZ: Float,
    ) : NavFootprint() {
        init {
            require(minX <= maxX && minZ <= maxZ) { "NavFootprint.Rect min must not exceed max; was ($minX, $minZ)..($maxX, $maxZ)." }
        }

        override fun contains(x: Float, z: Float): Boolean = x in minX..maxX && z in minZ..maxZ

        override fun overlapsCell(x: Float, z: Float, half: Float): Boolean =
            x + half >= minX && x - half <= maxX && z + half >= minZ && z - half <= maxZ
    }

    /**
     * A circle.
     *
     * @property centerX World X of the centre.
     * @property centerZ World Z of the centre.
     * @property radius Metres from the centre to the edge.
     */
    class Circle(val centerX: Float, val centerZ: Float, val radius: Float) : NavFootprint() {
        init {
            require(radius >= 0f && radius.isFinite()) { "NavFootprint.Circle radius must be non-negative and finite; was $radius." }
        }

        override val minX: Float get() = centerX - radius
        override val minZ: Float get() = centerZ - radius
        override val maxX: Float get() = centerX + radius
        override val maxZ: Float get() = centerZ + radius

        override fun contains(x: Float, z: Float): Boolean {
            val dx = x - centerX
            val dz = z - centerZ
            return dx * dx + dz * dz <= radius * radius
        }

        /** The square's nearest point to the centre is inside the circle. */
        override fun overlapsCell(x: Float, z: Float, half: Float): Boolean {
            val dx = centerX - centerX.coerceIn(x - half, x + half)
            val dz = centerZ - centerZ.coerceIn(z - half, z + half)
            return dx * dx + dz * dz <= radius * radius
        }
    }
}

/**
 * A static collider as the bake sees it: a [footprint] that spans world heights [minY] to [maxY].
 *
 * Whether it blocks depends on the ground under each sample and on the agent. It blocks when its top
 * is more than [NavAgent.stepHeight] above the ground, so a kerb is stepped over, and its underside
 * is less than [NavAgent.height] above the ground, so an arch or a balcony is walked under.
 *
 * @property footprint The outline it covers.
 * @property minY World Y of its underside.
 * @property maxY World Y of its top.
 */
class NavObstacle(val footprint: NavFootprint, val minY: Float, val maxY: Float) {
    init {
        require(minY <= maxY) { "NavObstacle minY must not exceed maxY; was $minY..$maxY." }
    }

    /**
     * Whether this obstacle stops [agent] standing on ground at height [groundY].
     *
     * @param groundY World Y of the ground under the sample.
     * @param agent The body being placed.
     * @return True when the obstacle is too high to step over and too low to walk under.
     */
    fun blocks(groundY: Float, agent: NavAgent): Boolean =
        maxY > groundY + agent.stepHeight && minY < groundY + agent.height
}

/**
 * Water the agent will not wade into: ground more than [maxDepth] below [surfaceY] is blocked.
 *
 * A level and a depth, and nothing about how the water looks or where it is: that is the game's
 * water. A river or lake with more than one level is passed as [NavAreas.noWalk] footprints instead.
 *
 * @property surfaceY World Y of the water's surface.
 * @property maxDepth Metres of water the agent wades through. Zero keeps it out of any water.
 */
class NavWater(val surfaceY: Float, val maxDepth: Float = 0f) {
    init {
        require(maxDepth >= 0f) { "NavWater maxDepth must be non-negative; was $maxDepth." }
    }

    /**
     * Whether ground at [groundY] is too deep under this water to stand on.
     *
     * @param groundY World Y of the ground.
     * @return True when the water there is deeper than [maxDepth].
     */
    fun blocks(groundY: Float): Boolean = surfaceY - groundY > maxDepth
}

/**
 * Everything besides terrain that decides where an agent may stand, for one bake.
 *
 * @property obstacles Static colliders, tested against each sample's ground height.
 * @property noWalk Outlines blocked whatever is under them: a game's "keep out" areas.
 * @property water Water too deep to wade, or null for none.
 */
class NavAreas(
    val obstacles: List<NavObstacle> = emptyList(),
    val noWalk: List<NavFootprint> = emptyList(),
    val water: NavWater? = null,
) {
    /** The empty set, for a bake from terrain alone. */
    companion object {
        /** No obstacles, no areas, no water. */
        val NONE: NavAreas = NavAreas()
    }
}
