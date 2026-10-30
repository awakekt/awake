/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.audio.AudioPlayerFactory
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.InterpolatedSystem
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.platform.error.AppErrorSource
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.audio.AudioSource
import com.awakekt.awake.scene.audio.SceneAudioSource
import com.awakekt.awake.scene.authoring.infrastructure.gameplayInput
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.destroy
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.binding.renderableRequests
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneAssetResolver
import com.awakekt.awake.scene.runtime.SpawnedNode
import com.awakekt.awake.scene.runtime.attachRenderableComponents
import com.awakekt.awake.scene.runtime.schedule.guardedFor
import com.awakekt.awake.scene.scene2d.SortingLayers
import com.awakekt.awake.scene.scene2d.SpriteClipSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * Plays [project]'s scenes in one app scene, one at a time, for [runProject]: the playing scene's
 * entities, its systems in [fixedSystems] and [frameSystems], and the [SceneSwitch] that moves the
 * project to another of its scenes.
 *
 * A request reads the next scene off the frame thread, in [LoadedProject.sceneLoads]. The scene that
 * asked plays on meanwhile, and the next takes its place at a safe point on the frame thread: the start
 * of a fixed step, or of the frame's own systems once the fixed steps have run. Nothing else in the
 * frame runs then, so no system sees the world change under it.
 *
 * The next scene is put in before the last one is taken out: its systems are built and its entities
 * made and drawn first, so a scene that fails there is taken out again and the last plays on. Then the
 * last one's systems close, its sounds stop, its spawned nodes despawn, its entities go with every
 * other entity the world held, and its physics world is destroyed.
 *
 * The runner's own steps run inside `project-fixed` and `project-frame`, which the schedule guards as one
 * system each, so they contain their failures themselves: a step that throws must not end either system,
 * which would be skipped for the rest of the scene and stop the project from switching at all. A failure
 * reaches the app's `AppErrorHandler` once, and the runner is able to switch again at once.
 * - **A switch that fails before the last scene is taken out** is reported as `AppErrorSource.SceneSwitch`,
 *   with the scene's path as its subject and a retry that asks for it again, and the last scene plays on.
 * - **A switch that fails after** has no last scene to go back to, so the next one is the scene that
 *   plays. The teardown runs every step even when one throws, so none of the old scene stays in the
 *   world; the failure is reported in the same way, once the new scene is committed and set up.
 * - **A failure of the switch's own bookkeeping** is reported the same way, and the playing scene's
 *   systems still run that step.
 * - **Attaching the models of spawned nodes** is treated as a system: its failure is reported once as
 *   `AppErrorSource.System` with the subject `SpawnedModels`, and the attaching is skipped until the next
 *   scene. The scene's systems run that frame regardless.
 *
 * With no handler registered each failure is thrown, as before.
 */
// TooManyFunctions: the steps of a switch, each small so that its failure is contained where it can be told apart.
@Suppress("TooManyFunctions")
internal class ProjectSceneRunner(private val project: LoadedProject) : SceneSwitch {
    /** Resolves the playing scene's glTF models for the app's asset library, whichever scene plays. */
    val models = PlayingModels(project.models)

    /** Carries out a switch that has loaded, then runs the playing scene's fixed systems. */
    val fixedSystems: InterpolatedSystem = FixedSystems()

    /** Carries out a switch that has loaded, then gives spawned nodes their models and runs the frame systems. */
    val frameSystems: System = FrameSystems()

    private lateinit var runtime: SceneAppLifecycleRuntime

    // The manifest's, checked when the project loaded, so each layer is named once.
    private val sortingLayers = SortingLayers(project.manifest.sortingLayers)
    private var systems: SceneSystemSet? = null
    private var instance: Scene? = null

    // The platform's player, opened for the first scene with sound and kept for the ones after it rather
    // than opened again: a browser starts a page's sound only from a tap, which a switch is not. Closed
    // with the app's scene.
    private val audio = lazy { AudioPlayerFactory.create() }

    private val loads = CoroutineScope(SupervisorJob() + project.sceneLoads)
    private val finished = Channel<FinishedLoad>(Channel.UNLIMITED)

    // Read and written on the frame thread only.
    private var loadingPath: String? = null
    private var loading: Job? = null
    private var requests = 0
    private var stopped = false
    private var spawnedModelsFailed = false

    private val input: () -> GameplayInput = { runtime.gameplayInput() }
    private val spawner: (SceneNode) -> SpawnedNode = { node -> runtime.spawn(project, node) }

    init {
        project.played = true
    }

    /** Runs in [runtime]'s schedule: what its fixed-step system factory calls, before the scene populates. */
    fun bind(runtime: SceneAppLifecycleRuntime): InterpolatedSystem {
        this.runtime = runtime
        return fixedSystems
    }

    /** Plays the project's entry scene, as the runtime's scene populates. */
    fun start() {
        val scene = project.playing
        systems = systemsFor(scene)
        instance = runtime.instantiate(scene, project.componentRegistry)
        begin()
    }

    override fun request(path: String) {
        if (stopped || path == loadingPath) return
        loading?.cancel()
        val number = ++requests
        loadingPath = path
        loading = loads.launch {
            val result = project.readScene(path)
            // A load that finishes as the scene stops has nothing left to play it.
            if (finished.trySend(FinishedLoad(number, path, result)).isFailure) result.getOrNull()?.close()
        }
    }

    /** Stops playing when the app's scene stops: the systems close, and what still loads is dropped. */
    fun stop() {
        if (stopped) return
        stopped = true
        systems?.close()
        systems = null
        if (audio.isInitialized()) audio.value.dispose()
        loads.cancel()
        finished.close()
        while (true) {
            val load = finished.tryReceive().getOrNull() ?: break
            load.result.getOrNull()?.close()
        }
        project.spawnedModels.close()
    }

    /**
     * At a safe point: plays the scene the current request loaded, or logs why it can't. A failure that
     * the switch's steps do not contain themselves is reported here, so the step never ends the system
     * that called it; with no handler registered it is thrown.
     */
    // TooGenericExceptionCaught: whatever carrying out a switch throws must leave the system able to run, and to switch again.
    @Suppress("TooGenericExceptionCaught")
    private fun carryOut() {
        val path = loadingPath ?: return
        try {
            playFinishedLoad()
        } catch (failure: Exception) {
            report(failure, path)
        }
    }

    private fun playFinishedLoad() {
        var load = finished.tryReceive().getOrNull()
        while (load != null && load.number != requests) {
            // Replaced by a later request before it finished.
            load.result.getOrNull()?.close()
            load = finished.tryReceive().getOrNull()
        }
        if (load == null) return
        loadingPath = null
        loading = null
        load.result.fold(
            onSuccess = ::switchTo,
            onFailure = { failure -> refuse(runtime, load.path, project.playing.path, failure) { request(load.path) } },
        )
    }

    /** Logs a failure to play [path] and hands it to the app's handler with a retry, or throws it when there is none. */
    private fun report(failure: Exception, path: String) {
        log.error(failure) { "Switching to $path failed: ${failure.message}" }
        if (!runtime.reportError(AppErrorSource.SceneSwitch, failure, retry = { request(path) }, subject = path)) throw failure
    }

    // TooGenericExceptionCaught: a scene that fails as it is put in, whatever the reason, must leave the playing one running.
    @Suppress("TooGenericExceptionCaught")
    private fun switchTo(next: ProjectScene) {
        val world = runtime.world
        val leaving = project.playing
        val before = world.liveEntities()
        models.target = next.models
        var nextSystems: SceneSystemSet? = null
        var nextInstance: Scene? = null
        try {
            nextSystems = systemsFor(next)
            nextInstance = runtime.instantiate(next, project.componentRegistry)
        } catch (failure: Exception) {
            nextSystems?.close()
            world.destroyAllBut(before)
            models.target = leaving.models
            next.close()
            refuse(runtime, next.path, leaving.path, failure) { request(next.path) }
            return
        }
        // From here the next scene is in the world and the last is on its way out, so there is no going
        // back: the next scene plays whatever the teardown or the setup does, and a failure is reported
        // once it does.
        val teardownFailure = leave(world, leaving, before)
        project.playing = next
        systems = nextSystems
        instance = nextInstance
        spawnedModelsFailed = false
        var setupFailure: Exception? = null
        try {
            begin()
        } catch (failure: Exception) {
            setupFailure = failure
        }
        teardownFailure?.let { report(it, next.path) }
        setupFailure?.let { report(it, next.path) }
    }

    /**
     * Closes [leaving]: its systems, sounds, spawned nodes, every entity [before] held, and its physics
     * world. Each step runs even when an earlier one threw, so that none of the scene stays behind in the
     * world; the first failure is returned, with the others suppressed in it, and null when none threw.
     */
    // TooGenericExceptionCaught: a step of the teardown that throws must not keep the others from running.
    @Suppress("TooGenericExceptionCaught")
    private fun leave(world: World, leaving: ProjectScene, before: List<Entity>): Exception? {
        var failed: Exception? = null
        fun step(block: () -> Unit) {
            try {
                block()
            } catch (failure: Exception) {
                failed?.addSuppressed(failure) ?: run { failed = failure }
            }
        }
        val closing = systems
        systems = null
        step { closing?.close() }
        step {
            world.queryEach(AudioSource::class) { _, source -> source.stop() }
            if (audio.isInitialized()) audio.value.stopMusic()
        }
        step { leaving.despawnAll() }
        val released = instance
        instance = null
        step {
            released?.let { scene ->
                runtime.release(scene)
                scene.destroy()
            }
        }
        // What else joined the scene: a fallback camera, what its systems made, what the host added.
        step { before.forEach { if (world.isAlive(it)) world.destroy(it) } }
        step { leaving.close() }
        return failed
    }

    /** What every scene does once its entities are in: its canvas images, fonts and strings, its camera and its skinned clips. */
    private fun begin() {
        runtime.canvasImages = project.content[CoreSceneContent.CanvasImages].orEmpty()
        runtime.canvasFonts = project.content[CoreSceneContent.CanvasFonts].orEmpty()
        runtime.canvasStrings = project.strings
        runtime.sortingLayers = sortingLayers
        activatePrimaryCamera(runtime.world)
        runtime.startSkinnedAnimations(project.models)
    }

    /**
     * Gives the nodes spawned since the last frame their models, once those have loaded. A failure is
     * reported once and the attaching is skipped until the next scene, as a system that throws is; with no
     * handler registered it is thrown.
     */
    // TooGenericExceptionCaught: whatever attaching a model throws must not end the frame systems.
    @Suppress("TooGenericExceptionCaught")
    private fun attachSpawnedModels() {
        if (spawnedModelsFailed) return
        try {
            project.spawnedModels.attachLoaded(runtime)
        } catch (failure: Exception) {
            // Before the handler runs, so a handler that steps the frame again does not report it twice.
            spawnedModelsFailed = true
            if (!runtime.reportError(AppErrorSource.System, failure, subject = SPAWNED_MODELS)) {
                spawnedModelsFailed = false
                throw failure
            }
            log.error(failure) { "Attaching spawned models failed and is skipped until the next scene: ${failure.message}" }
        }
    }

    /**
     * The systems [scene] runs in the app's world, less what the app's infrastructure already runs over
     * it: `defaultInfrastructureSystems` advances sprite clips, and a second clip system would advance
     * them twice a frame.
     */
    private fun systemsFor(scene: ProjectScene): SceneSystemSet {
        val hostRuns = if (runtime.hasInfrastructureSystem(SpriteClipSystem::class)) setOf(SPRITE_CLIPS_SYSTEM) else emptySet()
        val built = sceneSystemsFor(scene.document, services(scene), project.capabilities, hostRuns)
        // Each system guarded, so one that throws is reported to the app's error handler and skipped, not
        // the frame. Built once per scene; the guard adds no allocation to a frame. The set still closes the systems themselves.
        return SceneSystemSet(built.fixed.map { it.guardedFor(runtime) }, built.frame.map { it.guardedFor(runtime) }, built::close)
    }

    private fun services(scene: ProjectScene): SceneHostServices {
        val services = SceneHostServices(input = input, renderer = runtime.renderer, physics = scene.physics, content = scene.content)
            .withSpawner(spawner)
            .withSceneSwitch(this)
        return if (scene.document.uses(SceneAudioSource::class)) services.withAudio(audio.value) else services
    }

    private inner class FixedSystems : InterpolatedSystem {
        override fun update(world: World, delta: Float) {
            carryOut()
            val fixed = systems?.fixed ?: return
            for (index in fixed.indices) fixed[index].update(world, delta)
        }

        override fun interpolate(world: World, alpha: Float) {
            systems?.interpolate(world, alpha)
        }
    }

    private inner class FrameSystems : System {
        override fun update(world: World, delta: Float) {
            carryOut()
            attachSpawnedModels()
            val frame = systems?.frame ?: return
            for (index in frame.indices) frame[index].update(world, delta)
        }
    }

    private class FinishedLoad(val number: Int, val path: String, val result: Result<ProjectScene>)
}

/** The glTF models of whichever scene plays, for the app's asset library, which outlives a switch. */
internal class PlayingModels(var target: GltfAssetResolver) : SceneAssetResolver {
    override fun canResolveMesh(name: String): Boolean = target.canResolveMesh(name)

    override fun createMesh(runtime: SceneAppLifecycleRuntime, name: String): Mesh? = target.createMesh(runtime, name)

    override fun canResolveMaterial(name: String): Boolean = target.canResolveMaterial(name)

    override fun createMaterial(runtime: SceneAppLifecycleRuntime, name: String): Material? = target.createMaterial(runtime, name)

    override fun materialDefaults(name: String): PbrMaterial? = target.materialDefaults(name)

    override fun materialDefaults(mesh: String, material: String): PbrMaterial? = target.materialDefaults(mesh, material)
}

/** The scene at [path], or why it can't be played. Cancelling the load throws, as cancelling does. */
@Suppress("TooGenericExceptionCaught") // An asset source, a decoder or a capability reports a bad file in its own type.
private suspend fun LoadedProject.readScene(path: String): Result<ProjectScene> = try {
    Result.success(loadScene(path))
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}

/**
 * Makes [scene]'s entities, attaching their components with [registry] or the global ones, and their
 * renderers. When a renderer can't be made, the holds the others took in the asset library are given
 * back before it throws; its entities stay for the caller to take out.
 */
private fun SceneAppLifecycleRuntime.instantiate(scene: ProjectScene, registry: SceneComponentRegistry?): Scene {
    val made = scene.document.instantiate(world, registry ?: SceneComponentRegistry())
    var drawn = false
    try {
        made.attachRenderableComponents { request -> spec.renderableFactory(this, request) }
        drawn = true
    } finally {
        if (!drawn) release(made)
    }
    return made
}

/** Gives back the holds [scene]'s renderers took in the asset library. */
private fun SceneAppLifecycleRuntime.release(scene: Scene) {
    val library = requireAssetLibrary()
    scene.renderableRequests.forEach(library::releaseRenderable)
}

/** Destroys every live entity [kept] doesn't hold: what a scene that failed to go in left behind. */
private fun World.destroyAllBut(kept: List<Entity>) {
    val keep = kept.toHashSet()
    liveEntities().filter { it !in keep }.forEach(::destroy)
}

/** Every live entity, copied: the world's own list changes as entities come and go. */
@Suppress("SpreadOperator") // An empty array, once per switch.
private fun World.liveEntities(): List<Entity> = query(*NO_COMPONENTS).toList()

/** A query naming no component types, which matches every live entity. */
private val NO_COMPONENTS = emptyArray<KClass<out Any>>()

/**
 * Logs that the scene at [path] can't be played, so the one at [playing] plays on, and hands the failure to
 * the app's error handler, if it registered one, with [retry] to ask for [path] again.
 */
private fun refuse(runtime: SceneAppLifecycleRuntime, path: String, playing: String, failure: Throwable, retry: () -> Unit) {
    log.error(failure) { "Can't play $path, so $playing plays on: ${failure.message}" }
    runtime.reportError(AppErrorSource.SceneSwitch, failure, retry = retry, subject = path)
}

private const val SPAWNED_MODELS = "SpawnedModels"

private val log = Logger("project.scenes")
