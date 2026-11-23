/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime.schedule

import com.awakekt.awake.core.host.FixedTimestepLoop
import com.awakekt.awake.ecs.InterpolatedSystem
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.UnscaledSystem
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.runtime.PlannedSystem
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneAppSpec
import com.awakekt.awake.scene.runtime.SceneSystemHandle
import com.awakekt.awake.scene.runtime.SceneSystemPhase
import com.awakekt.awake.scene.runtime.requireKnownSystemNames
import kotlin.reflect.KClass
import kotlin.time.TimeSource

/**
 * Owns one scene's ECS execution order.
 *
 * Systems retain their feature ownership; this type only assigns them to fixed, pre-animation,
 * frame, or standard infrastructure stages. It deliberately has no UI, renderer staging, document,
 * or app lifecycle responsibilities.
 */
class SceneSchedule internal constructor(
    private val spec: SceneAppSpec,
) {
    /**
     * The scene's fixed-step loop, capped at [SceneAppSpec.maxFixedStepsPerFrame] steps a frame. The
     * runtime gives it to systems as its [com.awakekt.awake.core.host.FixedStepReport].
     */
    internal val fixedTimestepLoop = FixedTimestepLoop(maxStepsPerFrame = spec.maxFixedStepsPerFrame)
    private val registeredSystems = linkedMapOf<SceneSystemHandle<out System>, System>()
    private val fixedSystems = mutableListOf<System>()

    /**
     * The subset of [fixedSystems] that can show a partly-elapsed step.
     *
     * Held separately rather than filtered per frame: this runs once per rendered frame and a
     * filter over every system would allocate at exactly that rate.
     */
    private val interpolatedSystems = mutableListOf<InterpolatedSystem>()
    private val preAnimationSystems = mutableListOf<System>()
    private val frameSystems = mutableListOf<System>()
    private lateinit var infrastructureSystems: List<System>

    // What runs each frame: the systems above, each guarded so one that throws is reported to the app's
    // error handler and skipped (see guardedFor). Built once; the lists above keep the systems themselves.
    private var guardedInfrastructure: List<System> = emptyList()
    private var preFrameTransform: System? = null

    // Each system's name for [observer], in step with the list it runs from; built once.
    private val fixedNames = mutableListOf<String>()
    private val interpolatedNames = mutableListOf<String>()
    private val preAnimationNames = mutableListOf<String>()
    private val frameNames = mutableListOf<String>()
    private var infrastructureNames: List<String> = emptyList()

    /** Told around each system run, when installed; see [SceneSystemObserver]. Null calls nothing. */
    internal var observer: SceneSystemObserver? = null

    /** The name [observer] was last told began and has not yet ended, or null between systems. */
    internal var openMark: String? = null
        private set

    /** Whether [advance] splits its time into [gameMs] and [renderMs]. Off, it reads no clock. */
    internal var timed = false

    /** Simulation time multiplier (`1f` normal, `< 1f` slow motion, `0f` hitstop); [UnscaledSystem]s ignore this. */
    internal var timeScale: Float = 1f

    private var gameNanos = 0L
    private var renderNanos = 0L

    /**
     * Fixed, pre-animation, and frame systems, interpolation included, in the last *completed*
     * [advance]. A system reading this mid-advance, such as a frame-time logger, sees the previous
     * frame whole rather than this one half-counted.
     */
    internal var gameMs: Float = 0f
        private set

    /** Infrastructure systems -- transforms, scene extraction, recording and present -- likewise. */
    internal var renderMs: Float = 0f
        private set

    /**
     * The systems the spec registered and did not disable, in the order they run: fixed, then
     * pre-animation, then frame. Infrastructure systems are not listed.
     */
    internal var planned: List<PlannedSystem> = emptyList()
        private set

    internal fun initialize(runtime: SceneAppLifecycleRuntime) {
        requireKnownSystemNames(spec.disabledSystems, spec.systems.map { it.handle.name }, "the scene app")
        val kept = spec.systems.filter { it.handle.name !in spec.disabledSystems && spec.hostRole.runs(it.role) }
        planned = listOf(SceneSystemPhase.Fixed, SceneSystemPhase.PreAnimation, SceneSystemPhase.Frame).flatMap { phase ->
            kept.filter { it.phase == phase }.map { PlannedSystem(it.handle.name, phase, it.role) }
        }
        infrastructureSystems = spec.infrastructureSystemsFactory(runtime)
        guardedInfrastructure = infrastructureSystems.map { it.guardedFor(runtime) }
        infrastructureNames = infrastructureSystems.map { "infrastructure:${it::class.simpleName ?: it}" }
        kept.forEach { registration ->
            val system = registration.factory(runtime)
            registeredSystems[registration.handle] = system
            val guarded = system.guardedFor(runtime)
            val name = registration.handle.name
            when (registration.phase) {
                SceneSystemPhase.Fixed -> {
                    fixedSystems += guarded
                    fixedNames += name
                    if (guarded is InterpolatedSystem) {
                        interpolatedSystems += guarded
                        interpolatedNames += "$name.interpolate"
                    }
                }
                SceneSystemPhase.PreAnimation -> {
                    preAnimationSystems += guarded
                    preAnimationNames += name
                }
                SceneSystemPhase.Frame -> {
                    frameSystems += guarded
                    frameNames += name
                }
            }
        }
        val transformIndex = infrastructureSystems.indexOfFirst { it is TransformSystem }
        preFrameTransform = when {
            transformIndex >= 0 -> if (frameSystems.isNotEmpty()) guardedInfrastructure[transformIndex] else null
            preAnimationSystems.isNotEmpty() -> TransformSystem().guardedFor(runtime)
            else -> null
        }
    }

    internal fun synchronize(world: World) {
        runFrame(world, 0f, 0f)
    }

    internal fun advance(
        world: World,
        delta: Float,
        fixedUpdate: (Float) -> Unit,
    ) {
        gameNanos = 0L
        renderNanos = 0L
        val simDelta = delta * timeScale.coerceAtLeast(0f)
        fixedTimestepLoop.advance(
            frameDelta = simDelta,
            fixedUpdate = { step ->
                gameNanos += timed {
                    runAll(fixedSystems, fixedNames, world, step, step)
                    observed(SceneSystemObserver.APP_UPDATE) { fixedUpdate(step) }
                }
            },
            render = { alpha ->
                // Before the frame systems, because they are what reads a Transform to draw it:
                // interpolating after them would show the blend one frame late.
                gameNanos += timed {
                    for (index in interpolatedSystems.indices) {
                        observed(interpolatedNames[index]) { interpolatedSystems[index].interpolate(world, alpha) }
                    }
                }
                runFrame(world, simDelta, delta)
            },
        )
        gameMs = gameNanos / NANOS_PER_MS
        renderMs = renderNanos / NANOS_PER_MS
    }

    // Safe: SceneSystemHandle<T> is the key used when the system was registered via
    // `register(handle, system)`, so the stored value is always the same T the caller holds.
    @Suppress("UNCHECKED_CAST")
    internal fun <T : System> system(handle: SceneSystemHandle<T>): T =
        registeredSystems[handle] as? T ?: error("System ${handle.name} not found")

    internal fun system(name: String): System =
        registeredSystems.entries.firstOrNull { it.key.name == name }?.value
            ?: error("System $name not found")

    /** Whether the infrastructure has a system of [type]; false before [initialize]. */
    internal fun runsInfrastructure(type: KClass<out System>): Boolean =
        ::infrastructureSystems.isInitialized && infrastructureSystems.any { type.isInstance(it) }

    /** The infrastructure's scene renderer, or null before [initialize] or when it has none. */
    internal val renderSystem: RenderSystem3D?
        get() = if (::infrastructureSystems.isInitialized) {
            infrastructureSystems.firstNotNullOfOrNull { it as? RenderSystem3D }
        } else {
            null
        }

    internal fun dispose() {
        registeredSystems.clear()
        fixedSystems.clear()
        interpolatedSystems.clear()
        preAnimationSystems.clear()
        frameSystems.clear()
        fixedNames.clear()
        interpolatedNames.clear()
        preAnimationNames.clear()
        frameNames.clear()
        infrastructureNames = emptyList()
        if (::infrastructureSystems.isInitialized) infrastructureSystems = emptyList()
        guardedInfrastructure = emptyList()
        preFrameTransform = null
    }

    private fun runFrame(world: World, simDelta: Float, unscaledDelta: Float) {
        gameNanos += timed { runAll(preAnimationSystems, preAnimationNames, world, simDelta, unscaledDelta) }
        renderNanos += timed {
            preFrameTransform?.let { transforms -> observed(PRE_FRAME_TRANSFORMS) { transforms.update(world, simDelta) } }
        }
        gameNanos += timed { runAll(frameSystems, frameNames, world, simDelta, unscaledDelta) }
        renderNanos += timed { runAll(guardedInfrastructure, infrastructureNames, world, simDelta, unscaledDelta) }
    }

    private fun runAll(systems: List<System>, names: List<String>, world: World, simDelta: Float, unscaledDelta: Float) {
        for (index in systems.indices) {
            val system = systems[index]
            val step = if (system is UnscaledSystem) unscaledDelta else simDelta
            observed(names[index]) { system.update(world, step) }
        }
    }

    /** Runs [block] between [observer]'s marks for [name], or bare when none is installed. */
    private inline fun observed(name: String, block: () -> Unit) {
        val observer = observer
        if (observer == null) {
            block()
            return
        }
        observer.before(name)
        openMark = name
        block()
        openMark = null
        observer.after(name)
    }

    /** Nanoseconds [block] took while [timed], else 0 without reading the clock. */
    private inline fun timed(block: () -> Unit): Long {
        if (!timed) {
            block()
            return 0L
        }
        val start = TimeSource.Monotonic.markNow()
        block()
        return start.elapsedNow().inWholeNanoseconds
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000f
        const val PRE_FRAME_TRANSFORMS = "infrastructure:pre-frame transforms"
    }
}
