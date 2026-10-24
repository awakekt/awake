/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.benchmark

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.intersects
import com.awakekt.awake.core.math.planes
import com.awakekt.awake.render.passes.ShadowCasterVolume
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.shadowCascadeUniforms
import com.awakekt.awake.scene.rendering.spatial.SpatialGrid
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Warmup

/**
 * What the spatial index is for, at the size that makes it worth having.
 *
 * [everyEntityTested] is what `RenderSystem3D` did before one existed: a frustum test per entity,
 * every frame, whether or not the entity is anywhere near the camera. [indexedQuery] is the same
 * answer through `SpatialGrid`, which skips whole columns.
 *
 * The scene is deliberately open-world shaped -- props spread over a couple of kilometres with a
 * camera seeing a slice of it -- because that is the distribution where an index pays. A scene
 * that fits on screen has nothing to skip, and [everyEntityTested] wins there; the grid is not a
 * free upgrade, it is a trade against scene size.
 *
 * [indexMaintenance] is the other half of the trade: keeping the grid current costs a pass over
 * the entities too, so a number for culling alone would be a half-truth.
 *
 * The caster benchmarks are the second query a shadowed frame makes: which entities can shadow
 * what the camera sees, whether or not it sees them. [castersEveryEntityTested] tests every
 * entity against the cascades' volume; [castersIndexedQuery] asks the grid, under the same
 * mid-height sun, and [castersIndexedQueryLowSun] under a sun near the horizon, whose volume
 * reaches furthest across the ground.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
open class SpatialCullingBenchmarks {

    private lateinit var bounds: List<Aabb>
    private lateinit var grid: SpatialGrid
    private val visible = ArrayList<Int>(EXPECTED_VISIBLE)
    private val casters = ArrayList<Int>(EXPECTED_VISIBLE)
    private val sunVolume = ShadowCasterVolume()
    private val lowSunVolume = ShadowCasterVolume()

    private val lens = Lens(
        eye = Vec3f(0f, 30f, 0f),
        center = Vec3f(0f, 0f, -1f),
        fovYRadians = 1f,
        near = 0.5f,
        far = 300f,
    )

    /** Initializes benchmark bounding boxes and test data. */
    @Setup
    fun setup() {
        // Deterministic scatter over 2km, one prop per ~20m, at ground level.
        bounds = List(PROP_COUNT) { i ->
            val x = ((i * PRIME_X) % SPREAD) - SPREAD / 2
            val z = ((i * PRIME_Z) % SPREAD) - SPREAD / 2
            Aabb(
                Vec3f(x.toFloat() - HALF_SIZE, 0f, z.toFloat() - HALF_SIZE),
                Vec3f(x.toFloat() + HALF_SIZE, HALF_SIZE * 2f, z.toFloat() + HALF_SIZE),
            )
        }
        grid = SpatialGrid(cellSize = CELL_SIZE)
        bounds.forEachIndexed { id, box -> grid.insert(id, box) }
        fitCascades(sunVolume, SceneLight(direction = Vec3f(0.4f, 1f, 0.3f), color = Vec3f(1f, 1f, 1f)))
        fitCascades(lowSunVolume, SceneLight(direction = Vec3f(1f, 0.2f, 0.3f), color = Vec3f(1f, 1f, 1f)))
    }

    /** The cascades a frame fits for [light] and this camera, out to the default shadow distance. */
    private fun fitCascades(volume: ShadowCasterVolume, light: SceneLight) {
        volume.clear()
        shadowCascadeUniforms(light, lens, ASPECT, ClipSpace.WebGpu).viewProjections.forEach(volume::addCascade)
    }

    /** One `Frustum.intersects` per entity -- and one plane rebuild inside each, which is what
     * the shared plane list in the indexed path removes. */
    @Benchmark
    fun everyEntityTested(): Int {
        var drawn = 0
        for (box in bounds) {
            if (Frustum.intersects(lens, ASPECT, box)) drawn += 1
        }
        return drawn
    }

    /**
     * Still per entity, but against planes built once.
     *
     * Here to attribute the win honestly: [everyEntityTested] rebuilds six planes inside every
     * call, so some of the gap to [indexedQuery] is that rebuild rather than the grid. This
     * separates the two.
     */
    @Benchmark
    fun everyEntityTestedSharedPlanes(): Int {
        val planes = Frustum.planes(lens, ASPECT)
        var drawn = 0
        for (box in bounds) {
            if (planes.intersects(box)) drawn += 1
        }
        return drawn
    }

    /** The same visible set, through the grid. */
    @Benchmark
    fun indexedQuery(): Int {
        visible.clear()
        grid.queryFrustum(lens, ASPECT, visible)
        return visible.size
    }

    /** What a frame pays to keep the index current when nothing has moved: the comparison that
     * decides an entity is where the grid already thinks it is. */
    @Benchmark
    fun indexMaintenance(): Int {
        bounds.forEachIndexed { id, box -> grid.insert(id, box) }
        return grid.size
    }

    /** Every entity against the cascades' caster volume: what a frame without an index pays. */
    @Benchmark
    fun castersEveryEntityTested(): Int {
        var casting = 0
        for (box in bounds) {
            if (sunVolume.admits(box)) casting += 1
        }
        return casting
    }

    /** The same casters, through the grid. */
    @Benchmark
    fun castersIndexedQuery(): Int {
        casters.clear()
        grid.queryShadowCasters(sunVolume, casters)
        return casters.size
    }

    /** The grid's caster query under a sun near the horizon, the widest volume a frame asks about. */
    @Benchmark
    fun castersIndexedQueryLowSun(): Int {
        casters.clear()
        grid.queryShadowCasters(lowSunVolume, casters)
        return casters.size
    }

    private companion object {
        const val PROP_COUNT = 10_000
        const val SPREAD = 2_000
        const val PRIME_X = 137
        const val PRIME_Z = 311
        const val HALF_SIZE = 1.5f
        const val CELL_SIZE = 64f
        const val ASPECT = 16f / 9f
        const val EXPECTED_VISIBLE = 512
    }
}
