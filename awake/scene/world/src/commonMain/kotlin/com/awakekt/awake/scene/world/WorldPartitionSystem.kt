/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.world

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.world.AsyncWorldCellStreamListener
import com.awakekt.awake.world.CellContent
import com.awakekt.awake.world.WorldCellCoord
import com.awakekt.awake.world.WorldCellState
import com.awakekt.awake.world.WorldPartitionConfig
import com.awakekt.awake.world.WorldPartitionProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Callbacks invoked when a spatial world cell enters or leaves the active streaming radius.
 */
interface WorldCellStreamListener {
    /**
     * Invoked when a world cell enters the active streaming radius and requires loading.
     *
     * @param world Active ECS world instance.
     * @param coord Spatial cell coordinate that entered the active radius.
     */
    fun onCellLoad(world: World, coord: WorldCellCoord)

    /**
     * Invoked when a world cell leaves the active streaming radius and requires unloading.
     *
     * @param world Active ECS world instance.
     * @param coord Spatial cell coordinate that left the active radius.
     */
    fun onCellUnload(world: World, coord: WorldCellCoord)
}

/**
 * ECS System managing 2D spatial grid cell streaming around an active observer position.
 *
 * Features:
 * - Dynamic cell activation based on spherical distance rings.
 * - Hysteresis band ([WorldPartitionConfig.unloadRadius] > [WorldPartitionConfig.loadingRadius])
 *   to prevent thrashing (rapid loading/unloading) at cell boundaries.
 * - An allocation-free scan in every frame that loads and unloads nothing, the observer moving or not.
 * - A [probe] of what it streams, kept every update, for tests and for
 *   [WorldPartitionDebugSource][com.awakekt.awake.world.WorldPartitionDebugSource].
 *
 * @property config Configuration settings controlling cell size and load/unload radii.
 * @property streamListener Synchronous cell load and unload lifecycle listener.
 * @property asyncStreamListener Asynchronous cell streaming listener, taking precedence over [streamListener] if provided.
 * @param loadScope Coroutine scope for running asynchronous cell load operations.
 */
class WorldPartitionSystem(
    val config: WorldPartitionConfig = WorldPartitionConfig(),
    var streamListener: WorldCellStreamListener? = null,
    var asyncStreamListener: AsyncWorldCellStreamListener? = null,
    private val loadScope: CoroutineScope? = null,
) : System {

    private val _activeCells = HashSet<WorldCellCoord>()

    // The same cells twice more, for the per-frame scan: packed keys answer "already streamed?"
    // without a key object per candidate, and a list walks them by index without an iterator.
    // All three change together, in stream() and clear().
    private val activeKeys = CellKeySet()
    private val activeList = ArrayList<WorldCellCoord>()

    /** In-flight loads, so a cell leaving the radius can cancel its own and only its own. */
    private val loadJobs = HashMap<WorldCellCoord, Job>()

    /**
     * Finished loads waiting for a frame thread.
     *
     * A channel rather than a list: this is written from whichever thread [loadScope] dispatched
     * to and read on the frame thread, and `Channel` is the thread-safe queue coroutines already
     * ships. Unlimited because dropping a load silently would leave a hole in the world -- see the
     * plan's note on why a cap needs a stated policy rather than a guess.
     */
    private val loaded = Channel<Pair<WorldCellCoord, CellContent>>(Channel.UNLIMITED)

    /** Active cell coordinates currently resident within the loading radius. */
    val activeCells: Set<WorldCellCoord> get() = _activeCells

    /**
     * What this system streams as of its last update: the observer, its cell and each active cell's
     * [WorldCellState]. Written every update whether or not anything reads it, with no allocation
     * in steady frames; a debug view reads it rather than this system's own sets.
     */
    val probe: WorldPartitionProbe = WorldPartitionProbe(config)

    private var lastObserverPos: Vec3f = Vec3f(Float.NaN, Float.NaN, Float.NaN)

    /**
     * Streams around the first [StreamObserver] entity that has a `Transform`.
     *
     * Self-driving as of the D28 streaming pass -- this used to be an empty body, so adding the
     * system to a scene did nothing until something else called [updateObserverPosition] by hand,
     * and nothing did. A scene with no observer is still a no-op, which is what a scene that has
     * not opted into streaming should cost.
     *
     * First, not nearest or merged: two observers would mean a union of two loaded regions and a
     * rule for which wins at the hysteresis boundary. One is what every consumer needs today, and
     * a second one silently halving the effective radius would be worse than this being explicit.
     */
    override fun update(world: World, delta: Float) {
        // Before recomputing the active set: content that finished loading last frame belongs in
        // the world before anything decides what else to stream.
        applyLoadedCells(world)
        var observed: Vec3f? = null
        world.family<Transform, StreamObserver>().forEach { _, transform, _ ->
            if (observed == null) observed = transform.position
        }
        val local = observed
        if (local == null) {
            probe.lostObserver()
            return
        }
        // Cell coordinates are absolute and outlive any shift, so the observer is converted up
        // rather than the grid being converted down: FloatingOriginSystem moving the world under
        // this observer must not move which cells are loaded. In a scene that never shifts the
        // origin is absent and this is the same position it always was.
        val origin = world.findWorldOrigin()
        stream(world, if (origin == null) local else origin.toAbsolute(local, absoluteObserver), local)
    }

    /** Scratch for the conversion above -- this runs every frame and allocates nothing. */
    private val absoluteObserver = Vec3f(0f, 0f, 0f)

    /**
     * Updates active streamed cells based on the observer's world-space position.
     *
     * @param world The active ECS world passed to stream callbacks.
     * @param observerPosition Camera or player ABSOLUTE $(X, Y, Z)$ position -- see
     * [WorldOrigin]. The two are the same in a scene with no floating origin, which is every
     * scene that has not added [FloatingOriginSystem].
     */
    fun updateObserverPosition(world: World, observerPosition: Vec3f) = stream(world, observerPosition, observerPosition)

    /** Streams around [observerPosition], absolute, which the world draws at [localPosition]. */
    private fun stream(world: World, observerPosition: Vec3f, localPosition: Vec3f) {
        val size = config.cellSize
        val loadRadiusSq = config.loadingRadius * config.loadingRadius
        val unloadRadiusSq = config.unloadRadius * config.unloadRadius

        val minCellX = floor((observerPosition.x - config.loadingRadius) / size).toInt()
        val maxCellX = ceil((observerPosition.x + config.loadingRadius) / size).toInt()
        val minCellZ = floor((observerPosition.z - config.loadingRadius) / size).toInt()
        val maxCellZ = ceil((observerPosition.z + config.loadingRadius) / size).toInt()

        // 1. Identify newly entered cells within loading radius. A cell's coordinate object is made
        // only when it starts loading, so a frame that loads nothing allocates nothing here.
        for (cz in minCellZ..maxCellZ) {
            for (cx in minCellX..maxCellX) {
                if (activeKeys.contains(cx, cz)) continue

                // Check distance from observer to cell center
                val cellCenterX = (cx + 0.5f) * size
                val cellCenterZ = (cz + 0.5f) * size
                val dx = observerPosition.x - cellCenterX
                val dz = observerPosition.z - cellCenterZ
                val distSq = dx * dx + dz * dz

                if (distSq <= loadRadiusSq) {
                    val coord = WorldCellCoord(cx, cz)
                    _activeCells.add(coord)
                    activeKeys.add(cx, cz)
                    activeList.add(coord)
                    beginLoad(world, coord)
                }
            }
        }

        // 2. Identify out-of-range cells beyond unload radius, and record the rest in the probe
        probe.begin(observerPosition, localPosition)
        var i = 0
        while (i < activeList.size) {
            val coord = activeList[i]
            val cellCenterX = (coord.x + 0.5f) * size
            val cellCenterZ = (coord.z + 0.5f) * size
            val dx = observerPosition.x - cellCenterX
            val dz = observerPosition.z - cellCenterZ
            val distSq = dx * dx + dz * dz

            if (distSq > unloadRadiusSq) {
                _activeCells.remove(coord)
                activeKeys.remove(coord.x, coord.z)
                // Swap-remove: the last cell takes this slot and is checked next, so i stays.
                activeList[i] = activeList[activeList.lastIndex]
                activeList.removeAt(activeList.lastIndex)
                // Cancel first: the job checks nothing itself, so this is what stops a load whose
                // cell is already gone from finishing and being applied.
                loadJobs.remove(coord)?.cancel()
                asyncStreamListener?.onCellUnload(world, coord)
                streamListener?.onCellUnload(world, coord)
            } else {
                val state = when {
                    coord in loadJobs -> WorldCellState.Reading
                    distSq > loadRadiusSq -> WorldCellState.Leaving
                    else -> WorldCellState.Shown
                }
                probe.addCell(coord.x, coord.z, state)
                i++
            }
        }

        lastObserverPos = observerPosition
    }

    /**
     * Starts [coord]'s load, asynchronously when a scope and async listener were supplied.
     *
     * No guard against a duplicate job: the caller only reaches here for a coord that was absent
     * from [_activeCells], and an unload removes it from both sets together.
     */
    private fun beginLoad(world: World, coord: WorldCellCoord) {
        val async = asyncStreamListener
        val scope = loadScope
        if (async != null && scope != null) {
            loadJobs[coord] = scope.launch {
                val content = async.loadCell(coord)
                loaded.trySend(coord to content)
            }
            return
        }
        streamListener?.onCellLoad(world, coord)
    }

    /**
     * Applies every finished load whose cell is still active.
     *
     * The active check is the second half of cancellation: a job cancelled between its load
     * returning and this running has already queued its result, and applying it would populate a
     * cell the observer has left.
     */
    fun applyLoadedCells(world: World) {
        while (true) {
            val (coord, content) = loaded.tryReceive().getOrNull() ?: return
            loadJobs.remove(coord)
            if (coord in _activeCells) content.applyTo(world)
        }
    }

    /**
     * Clears all active streamed cells and unloads all active entries.
     */
    fun clear(world: World) {
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
        while (loaded.tryReceive().isSuccess) Unit
        for (coord in _activeCells) {
            asyncStreamListener?.onCellUnload(world, coord)
            streamListener?.onCellUnload(world, coord)
        }
        _activeCells.clear()
        activeKeys.clear()
        activeList.clear()
        probe.reset()
        lastObserverPos = Vec3f(Float.NaN, Float.NaN, Float.NaN)
    }
}
