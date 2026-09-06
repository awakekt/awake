/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.ai.behavior.ChaseAiSystem
import com.awakekt.awake.ai.behavior.ChaseBehavior
import com.awakekt.awake.ai.behavior.FleeBehavior
import com.awakekt.awake.ai.behavior.PatrolBehavior
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.toPositionNormalColorMesh
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.PathRequest
import com.awakekt.awake.navigation.PathRequestSystem
import com.awakekt.awake.navigation.grid.AgentRoute
import com.awakekt.awake.navigation.grid.CoarseNavGraph
import com.awakekt.awake.navigation.grid.HierarchicalNavGrid
import com.awakekt.awake.navigation.grid.NavGridCellStreamer
import com.awakekt.awake.navigation.grid.StreamedNavGrid
import com.awakekt.awake.navigation.grid.bakeNavGridCell
import com.awakekt.awake.navigation.grid.navGridDebugLines
import com.awakekt.awake.navigation.grid.summarize
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.Scene
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.world.CompositeCellStreamListener
import com.awakekt.awake.scene.world.StreamObserver
import com.awakekt.awake.scene.world.WorldCellCoord
import com.awakekt.awake.scene.world.WorldPartitionConfig
import com.awakekt.awake.scene.world.WorldPartitionSystem
import com.awakekt.awake.showcase.ShowcaseDebugToggles
import com.awakekt.awake.showcase.examples.streaming.MeshCellStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.math.cos
import kotlin.math.sin

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
 * and the reason that composite exists.
 *
 * **The target is deliberately further away than the world is loaded.** It orbits at
 * [TARGET_ORBIT_RADIUS] metres while only a few cells around the chaser exist, so the chaser cannot
 * see a route to it and `HierarchicalNavGrid` has to plan one through cells that are described but
 * not resident — a leg at a time, re-asked as the ground it is walking on streams in behind it.
 * The chaser is the streaming observer for the same reason: cells should follow the thing that
 * walks, not the thing it is walking towards.
 *
 * Coarse summaries for the whole demonstration area are seeded up front, which is what a real game
 * ships as build output. Without them the coarse graph knows only cells that have been resident,
 * and the first long walk has nothing to plan against.
 */
internal object StreamedNavExampleDriver {
    private const val CELL_SAMPLES = 16
    private const val SAMPLE_SIZE = 1f
    private const val CELL_SIZE = CELL_SAMPLES * SAMPLE_SIZE

    const val TARGET_ORBIT_RADIUS = 90f
    private const val TARGET_ORBIT_SPEED = 0.035f

    /** Cells summarised around the origin at startup: enough to cover the whole orbit and inside it. */
    private const val SEEDED_CELL_RADIUS = 8
    private const val CUBE_HALF_HEIGHT = 0.5f

    /** Metres to look outward for open ground when a spawn lands on a pillar. */
    private const val OPEN_GROUND_SEARCH = 12

    private const val CAMERA_HEIGHT = 34f
    private const val CAMERA_BACK = 34f

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
    private var cameraEntity: Entity? = null
    private var elapsed = 0f

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
                StreamedNavTerrain.heightAt(originX + x * SAMPLE_SIZE, originZ + z * SAMPLE_SIZE)
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
        // Streaming follows the chaser: it is the thing that walks, and the target is far outside
        // the loaded set on purpose. Streaming around the target instead would load the ground at
        // the destination and leave the walker on terrain nobody had baked.
        runtime.world.add(chaser.entity, StreamObserver)
        cameraEntity = instance.roots.find { it.name == "camera" }?.entity
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
        chaserTransform?.let(::standOnOpenGround)
        grid.clear()
        seedCoarseSummaries()
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
            if (height > StreamedNavTerrain.PILLAR_HEIGHT / 2f) ROCK_COLOR else GRASS_COLOR
        }

    /**
     * Describes the demonstration area to the coarse graph, without loading any of it.
     *
     * A real game bakes this offline and ships it; here the height function answers for any cell,
     * so the same summaries are cheap to compute at startup. Tiles are summarised and dropped —
     * keeping them would defeat the point, which is routing across ground that is *not* resident.
     */
    private fun seedCoarseSummaries() {
        coarse.clear()
        for (cz in -SEEDED_CELL_RADIUS..SEEDED_CELL_RADIUS) {
            for (cx in -SEEDED_CELL_RADIUS..SEEDED_CELL_RADIUS) {
                val coord = WorldCellCoord(cx, cz)
                val tile = cellHeightmap(coord).bakeNavGridCell(CELL_SAMPLES, SAMPLE_SIZE)
                coarse.put(coord, tile.summarize())
            }
        }
    }

    /**
     * Takes back everything this showcase put in the world that the scene did not.
     *
     * Streamed terrain entities are spawned by [MeshCellStreamer], not by the scene document, so
     * closing the scene leaves them drawing under whatever runs next. The navigation state goes
     * too: a re-activation seeds it again, and holding stale cells would let a search answer from
     * terrain that is no longer anywhere.
     */
    fun detach(runtime: SceneAppLifecycleRuntime) {
        meshStreamer?.dispose(runtime.world)
        meshStreamer = null
        partitionSystem = null
        grid.clear()
        coarse.clear()
        targetTransform = null
        chaserTransform = null
        cameraEntity = null
        runtime.requireAssetLibrary().releaseMaterial(TERRAIN_MATERIAL)
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

        val chaser = chaserTransform
        chaser?.let(::settleOnGround)
        chaser?.let { followWith(runtime, it) }
        // One call with everything: drawDebugLines replaces the frame's line buffer, so a second
        // call would erase the first, and not calling it at all leaves the last frame on screen.
        runtime.renderer.drawDebugLines(debugLines(runtime, chaser, target))
    }

    /** Rides above and behind the chaser, so streaming is watched from the thing it follows. */
    private fun followWith(runtime: SceneAppLifecycleRuntime, chaser: Transform) {
        val entity = cameraEntity ?: return
        val camera = runtime.world.get<Camera>(entity) ?: return
        camera.lens.eye.set(chaser.position.x, chaser.position.y + CAMERA_HEIGHT, chaser.position.z + CAMERA_BACK)
        camera.lens.center.set(chaser.position)
    }

    private fun debugLines(
        runtime: SceneAppLifecycleRuntime,
        chaser: Transform?,
        target: Transform,
    ): List<LineSegment> {
        val lines = ArrayList<LineSegment>()
        if (ShowcaseDebugToggles.showNavGrid) {
            lines += navGridDebugLines(grid, StreamedNavTerrain::heightAt, agentRoutes(runtime.world))
        }
        if (ShowcaseDebugToggles.showCorridor && chaser != null) {
            lines += corridorLines(
                coarse,
                cellOf(chaser.position, CELL_SIZE),
                cellOf(target.position, CELL_SIZE),
                CELL_SIZE,
                StreamedNavTerrain::heightAt,
            )
        }
        return lines
    }

    /**
     * Nudges a spawn point off a pillar.
     *
     * The scene file places the chaser at a fixed spot and the terrain is a function, so nothing
     * guarantees that spot is walkable — and a chaser standing on a pillar has an unwalkable start
     * sample, which makes every search fail and reads as the demonstration being broken. Rather
     * than hand-tuning a position against a formula, find the nearest open ground.
     */
    private fun standOnOpenGround(transform: Transform) {
        val open = StreamedNavTerrain.nearestOpenGround(
            transform.position.x,
            transform.position.z,
            OPEN_GROUND_SEARCH,
        )
        if (open != null) transform.position.set(open.first, transform.position.y, open.second)
        settleOnGround(transform)
    }

    /** Navigation carries X and Z only, so something has to put a moving cube back on the ground. */
    private fun settleOnGround(transform: Transform) {
        // The DRAWN surface, not the height function the mesh was sampled from: see
        // StreamedNavTerrain.surfaceAt. The camera rides this cube, so a centimetre of
        // disagreement here is the whole frame moving.
        transform.position.y =
            StreamedNavTerrain.surfaceAt(transform.position.x, transform.position.z, SAMPLE_SIZE) +
            CUBE_HALF_HEIGHT
    }

    /** Registered by the showcase module's asset block; streamed terrain borrows it. */
    private const val TERRAIN_MATERIAL = "lit-shadow"

    private val GRASS_COLOR = Color(r = 0.20f, g = 0.45f, b = 0.18f)
    private val ROCK_COLOR = Color(r = 0.45f, g = 0.43f, b = 0.47f)
}

/**
 * Every route follower's current path, for the nav-grid overlay. See
 * [com.awakekt.awake.navigation.grid.AgentRoute] for why the overlay is given these
 * rather than reading them out of the world itself.
 */
private fun agentRoutes(world: World): List<AgentRoute> = buildList {
    world.queryEach<ChaseBehavior> { entity, chase -> add(AgentRoute(entity, chase.path)) }
    world.queryEach<PatrolBehavior> { entity, patrol -> add(AgentRoute(entity, patrol.path)) }
    world.queryEach<FleeBehavior> { entity, flee -> add(AgentRoute(entity, flee.path)) }
}
