/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase.examples

import io.github.awakelab.awake.asset.terrain.Heightmap
import io.github.awakelab.awake.asset.terrain.toPositionNormalColorMesh
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.scene.ai.ChaseAiSystem
import io.github.awakelab.awake.scene.ai.ChaseBehavior
import io.github.awakelab.awake.scene.core.components.Transform
import io.github.awakelab.awake.scene.navigation.PathRequest
import io.github.awakelab.awake.scene.navigation.PathRequestSystem
import io.github.awakelab.awake.scene.navigation.grid.NavGrid
import io.github.awakelab.awake.scene.navigation.grid.bakeNavGrid
import io.github.awakelab.awake.scene.navigation.grid.navGridDebugLines
import io.github.awakelab.awake.scene.runtime.Scene
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.showcase.ShowcaseDebugToggles
import kotlin.math.cos
import kotlin.math.sin

/**
 * A cube that chases another cube across terrain, around a wall it cannot climb.
 *
 * The wall is terrain, not a collider: it is a ridge in this example's own [heightmap], and the
 * chaser avoids it only because [bakeNavGrid] read the slope and marked those samples unwalkable.
 * That is the point of the demonstration — nothing here tells the chaser a wall exists.
 *
 * The target slides between the two sides of the ridge, so the route flips end to end every few
 * seconds and the chaser has to re-plan rather than settling into a straight follow.
 */
internal object NavChaseExampleDriver {
    private const val EXTENT = 17
    private const val WALL_X = 8
    private const val WALL_LAST_Z = 11
    private const val WALL_HEIGHT = 4f
    private const val GROUND_ROLL = 0.25f
    private const val ROLL_FREQUENCY = 0.6f

    private const val CUBE_HALF_HEIGHT = 0.5f
    private const val TARGET_Z = 3f
    private const val TARGET_NEAR_X = 3f
    private const val TARGET_FAR_X = 13f
    private const val TARGET_CYCLE = 0.45f

    private const val CHASER_SPEED = 3f
    private const val CHASER_REPATH_INTERVAL = 0.4f
    private const val CHASER_WAYPOINT_RADIUS = 0.4f

    /** Gently rolling ground split by an impassable ridge, open only past its far end. */
    val heightmap = Heightmap(
        samples = FloatArray(EXTENT * EXTENT) { index ->
            val x = index % EXTENT
            val z = index / EXTENT
            val ground = GROUND_ROLL * sin(x * ROLL_FREQUENCY) * cos(z * ROLL_FREQUENCY)
            if (x == WALL_X && z <= WALL_LAST_Z) WALL_HEIGHT else ground
        },
        width = EXTENT,
        depth = EXTENT,
        scale = Vec3f(1f, 1f, 1f),
    )

    val geometry: MeshGeometry = heightmap.toPositionNormalColorMesh { _, _, height ->
        if (height > 1f) Color(r = 0.42f, g = 0.40f, b = 0.44f) else Color(r = 0.18f, g = 0.44f, b = 0.16f)
    }

    /** Exposed so a test can assert the ridge really blocks and the cubes really stand on ground. */
    val navGridTile = heightmap.bakeNavGrid(cellSize = 1f, maxSlopeDegrees = 45f)

    private val pathSystem = PathRequestSystem(NavGrid(navGridTile))
    private val chaseSystem = ChaseAiSystem()

    private var targetTransform: Transform? = null
    private var chaserTransform: Transform? = null
    private var elapsed = 0f

    fun attach(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        val target = instance.roots.find { it.name == "nav-target" } ?: return
        val chaser = instance.roots.find { it.name == "nav-chaser" } ?: return
        targetTransform = runtime.world.get<Transform>(target.entity)
        chaserTransform = runtime.world.get<Transform>(chaser.entity)
        runtime.world.add(
            chaser.entity,
            ChaseBehavior(
                target = target.entity,
                speed = CHASER_SPEED,
                repathInterval = CHASER_REPATH_INTERVAL,
                waypointRadius = CHASER_WAYPOINT_RADIUS,
            ),
        )
        runtime.world.add(chaser.entity, PathRequest())
        elapsed = 0f
    }

    fun advance(runtime: SceneAppLifecycleRuntime, delta: Float) {
        val target = targetTransform ?: return
        elapsed += delta

        val sweep = sin(elapsed * TARGET_CYCLE) * 0.5f + 0.5f
        target.position.x = TARGET_NEAR_X + (TARGET_FAR_X - TARGET_NEAR_X) * sweep
        target.position.z = TARGET_Z
        settleOnGround(target)

        chaseSystem.update(runtime.world, delta)
        pathSystem.update(runtime.world, delta)

        chaserTransform?.let(::settleOnGround)
        runtime.renderer.drawDebugLines(
            if (ShowcaseDebugToggles.showNavGrid) {
                navGridDebugLines(runtime.world, navGridTile, ::groundAt)
            } else {
                emptyList()
            },
        )
    }

    /**
     * Rides the terrain surface. Navigation is 2D by design — waypoints carry X and Z and leave Y
     * at zero — so something has to put a moving entity back on the ground, and
     * [Heightmap.heightAtWorld] is that something. Off the map it returns NaN, which leaves the
     * cube at its last height rather than teleporting it to the origin.
     */
    private fun settleOnGround(transform: Transform) {
        val surface = heightmap.heightAtWorld(transform.position.x, transform.position.z)
        if (!surface.isNaN()) transform.position.y = surface + CUBE_HALF_HEIGHT
    }

    /** Ground height for the debug wireframes, flattening the off-map NaN to zero. */
    private fun groundAt(x: Float, z: Float): Float {
        val surface = heightmap.heightAtWorld(x, z)
        return if (surface.isNaN()) 0f else surface
    }
}
