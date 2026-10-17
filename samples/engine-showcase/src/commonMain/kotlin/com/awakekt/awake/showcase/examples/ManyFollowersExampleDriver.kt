/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.project.runtime.PhysicsCanvasOcclusion
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.canvas.setCanvasOcclusion
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.frameStats
import com.awakekt.awake.showcase.oneDecimal
import com.awakekt.awake.ui.canvas.CanvasElement

/**
 * Hundreds of name plates over walking cubes, through Core's canvas follower rules: culled off
 * screen and beyond a distance, capped to the nearest of each group by the scene's
 * `canvas_follow_limit`s, shrunk and faded by distance, quest markers held to the screen's edge,
 * and plates hidden behind the pillars by physics rays within the scene's `canvas_follow_occlusion`.
 *
 * A readout at the bottom left shows the frame time, and [startBenchmark] runs a sweep over follower
 * counts that prints one `BENCH` line of frame-time percentiles a run, for a phone to be measured by.
 */
@Suppress("TooManyFunctions") // The panel's controls, the showcase's lifecycle and its frame, in one place as every showcase has them.
internal object ManyFollowersExampleDriver {
    /** The follower counts the panel offers and the benchmark sweeps. */
    val counts = listOf(100, 250, 500, 1_000)

    const val DEFAULT_COUNT = 500

    /** How many followers the next spawn makes; set from the launch options or the panel. */
    var count: Int = DEFAULT_COUNT
        private set

    /** How the plates follow now. */
    var rules = PlateRules()
        private set

    /** Whether the plates hide behind the pillars. */
    val occlusion: Boolean get() = rules.occlusion

    /**
     * Whether the plates shrink with distance. A scaled Text follower lays its text out again at a new
     * size each frame its distance changes, so this is the knob that compares what that costs.
     */
    val scaleByDistance: Boolean get() = rules.scale

    /** The last benchmark sweep's results, one line a run, newest last. */
    val results: List<String> get() = sweep?.results.orEmpty()

    /** Whether a benchmark sweep is running. */
    val benchmarking: Boolean get() = sweep?.finished == false

    private var crowd: FollowerCrowd? = null
    private var readout: CanvasElement? = null
    private var raysSystem: PhysicsCanvasOcclusion? = null
    private var pillars = emptyList<Entity>()
    private var sweep: FollowerBenchmarkSweep? = null
    private var time = 0f
    private var frame = 0
    private var requested: Int? = null
    private var benchmarkOnAttach = false
    private var raysLastFrame = 0
    private var laidOut = 0

    /** The occlusion system, made once the showcase's physics world and step exist. */
    private val rays: PhysicsCanvasOcclusion?
        get() = raysSystem ?: ShowcasePhysics.world?.let { physicsWorld ->
            ShowcasePhysics.physicsSystem?.let { bodies -> PhysicsCanvasOcclusion(physicsWorld, bodies).also { raysSystem = it } }
        }

    /** Sets the count before the showcase first activates, and whether it starts a benchmark, from the launch options. */
    fun preset(followers: Int, benchmark: Boolean) {
        require(followers > 0) { "A follower count must be positive." }
        count = followers
        benchmarkOnAttach = benchmark
    }

    /** Asks for [followers] followers from the next frame on. */
    fun request(followers: Int) {
        require(followers > 0) { "A follower count must be positive." }
        if (followers != count) requested = followers
    }

    /** Turns the plates' occlusion on or off. */
    fun setOcclusion(on: Boolean) {
        rules = rules.copy(occlusion = on)
        crowd?.apply(rules)
    }

    /** Turns the plates' shrinking with distance on or off. */
    fun setScaleByDistance(on: Boolean) {
        rules = rules.copy(scale = on)
        crowd?.apply(rules)
    }

    /** Runs the sweep: each count with occlusion on, then off, then the default count without shrinking. */
    fun startBenchmark() {
        val runs = counts.flatMap { listOf(FollowerBenchmarkRun(it, PlateRules(occlusion = true)), FollowerBenchmarkRun(it, PlateRules(occlusion = false))) }
        sweep = FollowerBenchmarkSweep(runs + FollowerBenchmarkRun(DEFAULT_COUNT, PlateRules(occlusion = true, scale = false)))
    }

    fun attach(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        // The UI phase is what this showcase is read by.
        runtime.perfStatsEnabled = true
        val world = runtime.world
        readout = instance.roots.firstOrNull { it.name == "readout" }?.let { world.get(it.entity, CanvasElement::class) }
        pillars = instance.roots.filter { world.get(it.entity, PhysicsBody::class) != null }.map { it.entity }
        crowd = FollowerCrowd(world, MeshRenderer(runtime.requireMesh("cube"), runtime.requireMaterial("lit-shadow"))).also { it.spawn(count, rules) }
        time = 0f
        frame = 0
        if (benchmarkOnAttach) {
            benchmarkOnAttach = false
            startBenchmark()
        }
    }

    fun detach(runtime: SceneAppLifecycleRuntime) {
        val world = runtime.world
        crowd?.dispose()
        crowd = null
        pillars.forEach { ShowcasePhysics.physicsSystem?.destroyBody(world, it) }
        pillars = emptyList()
        world.setCanvasOcclusion(null)
        raysSystem = null
        readout = null
        sweep = null
    }

    /** Moves the walkers, answers occlusion, turns some plates' numbers, and runs the readout and benchmark. */
    fun advance(runtime: SceneAppLifecycleRuntime, delta: Float) {
        val crowd = crowd ?: return
        startNextRun(crowd)
        requested?.let { next ->
            requested = null
            count = next
            crowd.spawn(next, rules)
        }
        time += delta
        frame++
        crowd.walk(time)
        // Read before the update starts a new budget: the rays the last frame's canvas cast.
        raysLastFrame = raysSystem?.raysCast ?: 0
        if (rules.occlusion) rays?.update(runtime.world, delta)
        crowd.turnNumbers(frame)
        laidOut = crowd.laidOut()
        measure(runtime, delta)
        if (frame % READOUT_EVERY == 0) readout?.text = readoutText(runtime, crowd)
    }

    /** Sets up the sweep's next run once the last is in: its count and rules. */
    private fun startNextRun(crowd: FollowerCrowd) {
        val next = sweep?.next() ?: return
        rules = next.rules
        if (next.followers != count || crowd.size != next.followers) {
            count = next.followers
            crowd.spawn(next.followers, rules)
        } else {
            crowd.apply(rules)
        }
    }

    private fun measure(runtime: SceneAppLifecycleRuntime, delta: Float) {
        val sweep = sweep ?: return
        val phases = runtime.frameStats().phases
        val ui = phases.uiBuildMs + phases.uiStageMs
        val line = sweep.record(delta * MILLIS, ui, phases.gameMs + phases.renderMs + ui, laidOut) ?: return
        println(line)
        if (sweep.finished) {
            setScaleByDistance(true)
            println("${FollowerBenchmarkSweep.PREFIX} done")
        }
    }

    private fun readoutText(runtime: SceneAppLifecycleRuntime, crowd: FollowerCrowd): String {
        val stats = runtime.frameStats()
        val ui = stats.phases.uiBuildMs + stats.phases.uiStageMs
        val occluding = if (rules.occlusion) "on, $raysLastFrame rays a frame" else "off"
        val head = "${crowd.size} followers, $laidOut plates laid out · scale ${onOff(rules.scale)} · occlusion $occluding"
        val times = "frame ${stats.frameTimeMs.oneDecimal()} ms (p99 ${stats.p99FrameTimeMs.oneDecimal()}) · ui ${ui.oneDecimal()} ms"
        val sweep = sweep
        val run = sweep?.run
        val status = when {
            run != null -> "benchmark ${run.followers}, occlusion ${onOff(run.rules.occlusion)}, scale ${onOff(run.rules.scale)}: ${(sweep.progress * PERCENT).toInt()}%"
            sweep != null && sweep.results.isNotEmpty() -> sweep.results.last()
            else -> "Debug → Run benchmark for percentiles"
        }
        return "$head\n$times\n$status"
    }

    private const val READOUT_EVERY = 15
    private const val MILLIS = 1000f
    private const val PERCENT = 100f
}
