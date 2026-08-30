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
import io.github.awakelab.awake.scene.navigation.grid.CoarseNavGraph
import io.github.awakelab.awake.scene.navigation.grid.HierarchicalNavGrid
import io.github.awakelab.awake.scene.navigation.grid.NavGridCellStreamer
import io.github.awakelab.awake.scene.navigation.grid.StreamedNavGrid
import io.github.awakelab.awake.scene.navigation.grid.navGridDebugLines
import io.github.awakelab.awake.scene.runtime.Scene
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.showcase.ShowcaseDebugToggles
import io.github.awakelab.awake.scene.rendering.streaming.MeshCellStreamer
import io.github.awakelab.awake.scene.world.CompositeCellStreamListener
import io.github.awakelab.awake.scene.world.StreamObserver
import io.github.awakelab.awake.scene.world.WorldCellCoord
import io.github.awakelab.awake.scene.world.WorldPartitionConfig
import io.github.awakelab.awake.scene.world.WorldPartitionSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A chaser that follows a target across a world nobody baked in advance.
 *
 * The other navigation showcase bakes one grid for one hand-authored heightmap. This one has no
 * authored terrain at all: `WorldPartitionSystem` decides which cells are near the moving target,
 * `NavGridCellStreamer` bakes each one's walkability off the frame thread as it arrives, and
 * `StreamedNavGrid` searches whatever is currently resident. The chaser crosses cell boundaries
 * without anything telling it they exist, and its path searches run off the frame thread too.
 *
 * Terrain streams with it. `MeshCellStreamer` builds each cell's mesh off the frame thread from
 * the same height function the navigation bake reads, and `CompositeCellStreamListener` runs both
 * from the partition system's single listener slot — which is the arrangement a real game needs
 * and the reason that composite exists. Ground appearing and disappearing at the streaming radius
 * is the demonstration; the navigation overlay behind the panel's checkbox shows what the chaser
 * is actually reasoning about over it.
 */
internal object StreamedNavExampleDriver {
    private const val CELL_SAMPLES = 16
    private const val SAMPLE_SIZE = 1f
    private const val CELL_SIZE = CELL_SAMPLES * SAMPLE_SIZE

    private const val GROUND_ROLL = 0.6f
    private const val ROLL_FREQUENCY = 0.13f
    private const val PILLAR_SPACING = 7f
    private const val PILLAR_RADIUS = 1.6f
    private const val PILLAR_HEIGHT = 6f

    private const val TARGET_ORBIT_RADIUS = 26f
    private const val TARGET_ORBIT_SPEED = 0.18f
    private const val ORBIT_LANE_HALF_WIDTH = 2f
    private const val CUBE_HALF_HEIGHT = 0.5f

    private const val CHASER_SPEED = 4f
    private const val CHASER_REPATH_INTERVAL = 0.5f
    private const val CHASER_WAYPOINT_RADIUS = 0.5f

    private val config = WorldPartitionConfig(
        cellSize = CELL_SIZE,
        loadingRadius = CELL_SIZE * 2f,
        unloadRadius = CELL_SIZE * 3f,
    )

    /** Exposed so a test can assert the world it bakes is worth walking across. */
    val grid = StreamedNavGrid(samplesPerCell = CELL_SAMPLES, sampleSize = SAMPLE_SIZE)

    /**
     * What each baked cell contributed, kept after it unloads.
     *
     * The chaser rarely needs it here — its target orbits inside the loading radius — but pathing
     * through it is what proves the two layers agree: a route that the fine grid can answer must
     * come back identical, or the hierarchy is changing answers it has no business changing.
     */
    private val coarse = CoarseNavGraph(CELL_SIZE)

    /**
     * Its own scope rather than the runtime's, which has none to lend. A showcase owning the
     * lifetime of its own background work is also the honest shape: nothing else should be
     * cancelled when this example is switched away from.
     */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val pathSystem = PathRequestSystem(
        HierarchicalNavGrid(grid, coarse),
        searchScope = backgroundScope,
    )
    private val chaseSystem = ChaseAiSystem()

    /**
     * Built in [attach] rather than here: streaming terrain needs a renderer and a material, and
     * neither exists until a scene is running. Rebuilt on every activation, which is also what
     * drops the previous run's cells — a showcase switched away from and back to starts empty.
     */
    private var partitionSystem: WorldPartitionSystem? = null

    /** Held so its retirement queue can be ticked, and so a re-activation can dispose the old one. */
    private var meshStreamer: MeshCellStreamer? = null

    private var targetTransform: Transform? = null
    private var chaserTransform: Transform? = null
    private var elapsed = 0f

    /**
     * Rolling ground with pillars too steep to climb, as a pure function of world position.
     *
     * A function rather than stored terrain because a streamed world has no single heightmap to
     * store: the same arithmetic answers for any cell, including one the observer has never
     * visited, which is what lets a cell be baked the moment it is needed.
     */
    fun heightAt(worldX: Float, worldZ: Float): Float {
        val ground = GROUND_ROLL * sin(worldX * ROLL_FREQUENCY) * cos(worldZ * ROLL_FREQUENCY)
        if (onOrbitLane(worldX, worldZ)) return ground
        val pillarX = worldX - round(worldX / PILLAR_SPACING) * PILLAR_SPACING
        val pillarZ = worldZ - round(worldZ / PILLAR_SPACING) * PILLAR_SPACING
        val onPillar = pillarX * pillarX + pillarZ * pillarZ < PILLAR_RADIUS * PILLAR_RADIUS
        return if (onPillar) PILLAR_HEIGHT else ground
    }

    /**
     * The ring the target walks is kept clear of pillars.
     *
     * Otherwise the lattice eventually puts one where the target is standing, and a target inside
     * an obstacle reads as the chaser having given up. The chaser still crosses the pillar field
     * to reach it, which is the part worth watching.
     */
    private fun onOrbitLane(worldX: Float, worldZ: Float): Boolean {
        val distance = sqrt(worldX * worldX + worldZ * worldZ)
        return abs(distance - TARGET_ORBIT_RADIUS) < ORBIT_LANE_HALF_WIDTH
    }

    /**
     * One cell's terrain, in that cell's local coordinates.
     *
     * A sample wider than the cell so the bake's slope probe has a neighbour to compare against at
     * the far edge instead of clamping onto itself, which would read a cliff there as flat.
     */
    private fun cellHeightmap(coord: WorldCellCoord): Heightmap {
        val samples = CELL_SAMPLES + 1
        val originX = coord.x * CELL_SIZE
        val originZ = coord.z * CELL_SIZE
        return Heightmap(
            samples = FloatArray(samples * samples) { index ->
                val x = index % samples
                val z = index / samples
                heightAt(originX + x * SAMPLE_SIZE, originZ + z * SAMPLE_SIZE)
            },
            width = samples,
            depth = samples,
            scale = Vec3f(SAMPLE_SIZE, 1f, SAMPLE_SIZE),
        )
    }

    fun attach(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        val target = instance.roots.find { it.name == "stream-target" } ?: return
        val chaser = instance.roots.find { it.name == "stream-chaser" } ?: return
        targetTransform = runtime.world.get<Transform>(target.entity)
        chaserTransform = runtime.world.get<Transform>(chaser.entity)
        // Streaming follows the target: it is what moves, and the chaser stays within a cell or
        // two of it. A camera-following observer would stream cells nobody walks on.
        runtime.world.add(target.entity, StreamObserver)
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
        grid.clear()
        // The previous activation's terrain, if any: its entities went with the closed scene, but
        // its GPU meshes are this driver's to free. The material reference goes back too, or every
        // switch away and back would leave the library holding one more than it handed out.
        meshStreamer?.let {
            it.dispose(runtime.world)
            runtime.requireAssetLibrary().releaseMaterial(TERRAIN_MATERIAL)
        }
        val terrain = MeshCellStreamer(
            renderer = runtime.renderer,
            material = runtime.requireAssetLibrary().requireMaterial(runtime, TERRAIN_MATERIAL),
            cellSize = CELL_SIZE,
            geometryFor = ::cellGeometry,
        )
        meshStreamer = terrain
        partitionSystem = WorldPartitionSystem(
            config,
            asyncStreamListener = CompositeCellStreamListener(
                NavGridCellStreamer(grid, config, ::cellHeightmap, coarse = coarse),
                terrain,
            ),
            loadScope = backgroundScope,
        )
        elapsed = 0f
    }

    /**
     * One cell's terrain mesh, in that cell's local space — [MeshCellStreamer] places it.
     *
     * Coloured by height rather than by material, which is the cheapest way to make streaming
     * visible: a pillar reads as rock and the rolling ground as grass, so a cell arriving is
     * obvious without a texture pipeline this sample does not have.
     */
    private fun cellGeometry(coord: WorldCellCoord): MeshGeometry =
        cellHeightmap(coord).toPositionNormalColorMesh { _, _, height ->
            if (height > PILLAR_HEIGHT / 2f) ROCK_COLOR else GRASS_COLOR
        }

    fun advance(runtime: SceneAppLifecycleRuntime, delta: Float) {
        val target = targetTransform ?: return
        elapsed += delta

        val angle = elapsed * TARGET_ORBIT_SPEED
        target.position.x = cos(angle) * TARGET_ORBIT_RADIUS
        target.position.z = sin(angle) * TARGET_ORBIT_RADIUS
        settleOnGround(target)

        // Streaming first: a chaser must never plan across a cell that this frame unloaded.
        partitionSystem?.update(runtime.world, delta)
        // Frees the meshes of cells that unloaded a few frames ago, once no in-flight frame can
        // still be drawing them.
        meshStreamer?.update(runtime.world, delta)
        chaseSystem.update(runtime.world, delta)
        pathSystem.update(runtime.world, delta)

        chaserTransform?.let(::settleOnGround)
        // Cleared explicitly when off rather than skipped: drawDebugLines replaces the frame's
        // line buffer, so not calling it leaves the last frame's markers on screen forever.
        runtime.renderer.drawDebugLines(
            if (ShowcaseDebugToggles.showNavGrid) {
                navGridDebugLines(runtime.world, grid, ::heightAt)
            } else {
                emptyList()
            },
        )
    }

    /** Navigation carries X and Z only, so something has to put a moving cube back on the ground. */
    private fun settleOnGround(transform: Transform) {
        transform.position.y = heightAt(transform.position.x, transform.position.z) + CUBE_HALF_HEIGHT
    }

    /** `kotlin.math.round` returns a Double for Float input on some targets; keep it Float here. */
    private fun round(value: Float): Float = (value + if (value < 0f) -HALF else HALF).toInt().toFloat()

    private const val HALF = 0.5f

    /** Registered by the showcase module's asset block; streamed terrain borrows it. */
    private const val TERRAIN_MATERIAL = "lit-shadow"

    private val GRASS_COLOR = Color(r = 0.20f, g = 0.45f, b = 0.18f)
    private val ROCK_COLOR = Color(r = 0.45f, g = 0.43f, b = 0.47f)
}
