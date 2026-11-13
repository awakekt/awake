/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.audio.AudioPlayer
import com.awakekt.awake.core.host.FixedStepReport
import com.awakekt.awake.ecs.InterpolatedSystem
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.platform.error.childScope
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.runtime.PlannedSystem
import com.awakekt.awake.scene.runtime.SceneAssetReferences
import com.awakekt.awake.scene.runtime.ScenePrewarm
import com.awakekt.awake.scene.runtime.SceneSystemPhase
import com.awakekt.awake.scene.runtime.SpawnedNode
import com.awakekt.awake.scene.runtime.requireKnownSystemNames
import kotlinx.coroutines.CoroutineScope

/**
 * What a scene's systems need from the host and cannot read from the scene itself. A host that draws
 * passes its renderer; a game server or a test makes one with [headless], and the systems that draw
 * are left out.
 */
@Suppress("TooManyFunctions") // Each optional host service has its own builder.
class SceneHostServices private constructor(
    private val gpu: Renderer?,
    /** The keyboard, pointer and touch state, with what the UI owns of it. Read every frame. */
    val input: () -> GameplayInput,
    /** The physics world the scene's bodies and characters live in; null runs the scene without physics. */
    val physics: PhysicsWorld?,
    /** What the scene's capabilities read from the project's files, as [loadSceneContent] reads it. */
    val content: SceneContent,
    private val spawner: ((SceneNode) -> SpawnedNode)? = null,
    /** What the scene's sounds play through; null plays them silently. A host adds one with [withAudio]. */
    val audio: AudioPlayer? = null,
    /**
     * Where a system asks to play another of the project's scenes, as a menu's Play button or a level's
     * exit does: `runProject` gives one. Null where nothing switches scenes, such as an editor playing the
     * scene it edits or a host that runs these systems in a world of its own, unless it adds its own
     * with [withSceneSwitch].
     */
    val sceneSwitch: SceneSwitch? = null,
    /**
     * Where a system reads what the [sceneSwitch] is doing: loading, ready or failed. `runProject` gives
     * one; null wherever [sceneSwitch] is, unless the host adds its own with [withSceneSwitchStatus].
     */
    val sceneSwitchStatus: SceneSwitchStatus? = null,
    /**
     * The fixed-step loop the scene's [SceneSystemSet.fixed] systems run in, as they read it: the steps
     * each frame ran and the time it dropped once it had run as many as its cap allows. `runProject`
     * gives the scene's own, as configured by `maxFixedStepsPerFrame`. Null where the host gave none:
     * a host that steps the systems with its own [com.awakekt.awake.core.host.FixedTimestepLoop] adds it
     * with [withFixedStep].
     *
     * A system that keeps a clock of its own in step with the fixed steps reads it, or adds a
     * [com.awakekt.awake.core.host.TimeDroppedListener] to it, instead of working the dropped time out
     * from the frame's delta: a networked game that predicts its local player sends or replays the
     * steps it dropped, so the player walks as long as the server does.
     */
    val fixedStep: FixedStepReport? = null,
    /**
     * The scope the scene's systems run their background work in, such as reading terrain cells or
     * resolving surfaces: a child of the scene session's scope, so it stops when the session is
     * disposed. `runProject` gives the session's own. Null where the host gave none; a host whose scene
     * reads anything in the background adds its own with [withCoroutineScope]. Without one, those
     * systems fall back to a scope nobody owns, and a warning is logged once. Each system makes its
     * own child of it and cancels that when it closes.
     */
    val coroutineScope: CoroutineScope? = null,
    private val prewarmer: ((SceneNode, (Int, Int) -> Unit) -> ScenePrewarm)? = null,
    private val assetPrewarmer: ((SceneAssetReferences, (Int, Int) -> Unit) -> ScenePrewarm)? = null,
) {
    /** Services for a host that draws through [renderer]. */
    constructor(
        input: () -> GameplayInput,
        renderer: Renderer,
        physics: PhysicsWorld? = null,
        content: SceneContent = SceneContent.Empty,
    ) : this(renderer, input, physics, content)

    /**
     * What systems create GPU content through, such as particle sprites and shader effects. Throws on a
     * [headless] host, whose plan leaves out every system that would ask; see [hasRenderer].
     */
    val renderer: Renderer
        get() = checkNotNull(gpu) { "This scene host is headless: it has no renderer" }

    /** Whether there is a [renderer]: false on a host made by [headless]. */
    val hasRenderer: Boolean get() = gpu != null

    /** Whether [spawn] works: true once the host gave a spawner with [withSpawner], as `runProject` does. */
    val canSpawn: Boolean get() = spawner != null

    /**
     * Puts [node] into the running scene, as the host's `spawn` does, for whatever joins the scene while
     * it plays: a player arriving over the network, a monster a server spawns, a drop. Throws when the
     * host gave no spawner; see [canSpawn].
     */
    fun spawn(node: SceneNode): SpawnedNode =
        checkNotNull(spawner) { "This scene host can't spawn; its host passes a spawner with withSpawner" }(node)

    /** Whether the host installed [prewarm] with [withPrewarmer], as `runProject` does. */
    val canPrewarm: Boolean get() = prewarmer != null

    /** Whether the host installed a prewarmer for batches of [SceneAssetReferences]. */
    val canPrewarmAssets: Boolean get() = assetPrewarmer != null

    /**
     * Prepares the assets [node] and its children name before [spawn]. Call from a running system on
     * the frame thread. [onProgress] runs on that thread as each asset settles. Keep the returned
     * [ScenePrewarm] open until the spawn can share its assets, then close it when the hold is no
     * longer needed. Throws when the host gave no prewarmer. See [canPrewarm].
     */
    fun prewarm(node: SceneNode, onProgress: (settled: Int, total: Int) -> Unit = { _, _ -> }): ScenePrewarm =
        checkNotNull(prewarmer) { "This scene host can't prewarm nodes; its host passes a prewarmer with withPrewarmer" }(node, onProgress)

    /**
     * Prepares [references] under one [ScenePrewarm] hold. Combine the references of several prefabs
     * with [SceneAssetReferences.plus]. Uses the same frame-thread and progress rules as node prewarming.
     * Throws when the host gave no asset prewarmer. See [canPrewarmAssets].
     */
    fun prewarm(references: SceneAssetReferences, onProgress: (settled: Int, total: Int) -> Unit = { _, _ -> }): ScenePrewarm =
        checkNotNull(assetPrewarmer) { "This scene host can't prewarm asset references; pass an asset prewarmer with withPrewarmer" }(
            references,
            onProgress,
        )

    /** These services, with [prewarm] preparing nodes through [prewarmer]. */
    fun withPrewarmer(prewarmer: (SceneNode, (Int, Int) -> Unit) -> ScenePrewarm): SceneHostServices =
        SceneHostServices(gpu, input, physics, content, spawner, audio, sceneSwitch, sceneSwitchStatus, fixedStep, coroutineScope, prewarmer, assetPrewarmer)

    /** These services, with [prewarm] preparing nodes through [prewarmer] and batches through [assetPrewarmer]. */
    fun withPrewarmer(
        prewarmer: (SceneNode, (Int, Int) -> Unit) -> ScenePrewarm,
        assetPrewarmer: (SceneAssetReferences, (Int, Int) -> Unit) -> ScenePrewarm,
    ): SceneHostServices =
        SceneHostServices(gpu, input, physics, content, spawner, audio, sceneSwitch, sceneSwitchStatus, fixedStep, coroutineScope, prewarmer, assetPrewarmer)

    /** These services, with [spawn] putting nodes into the scene through [spawner]. */
    fun withSpawner(spawner: (SceneNode) -> SpawnedNode): SceneHostServices =
        SceneHostServices(gpu, input, physics, content, spawner, audio, sceneSwitch, sceneSwitchStatus, fixedStep, coroutineScope, prewarmer, assetPrewarmer)

    /** These services, with the scene's `audio_source`s playing through [audio], as `runProject` does. */
    fun withAudio(audio: AudioPlayer): SceneHostServices =
        SceneHostServices(gpu, input, physics, content, spawner, audio, sceneSwitch, sceneSwitchStatus, fixedStep, coroutineScope, prewarmer, assetPrewarmer)

    /** These services, with [sceneSwitch] carrying out the systems' requests for another scene, as `runProject` does. */
    fun withSceneSwitch(sceneSwitch: SceneSwitch): SceneHostServices =
        SceneHostServices(gpu, input, physics, content, spawner, audio, sceneSwitch, sceneSwitchStatus, fixedStep, coroutineScope, prewarmer, assetPrewarmer)

    /** These services, with [sceneSwitchStatus] reporting what the [sceneSwitch] is doing, as `runProject` does. */
    fun withSceneSwitchStatus(sceneSwitchStatus: SceneSwitchStatus): SceneHostServices =
        SceneHostServices(gpu, input, physics, content, spawner, audio, sceneSwitch, sceneSwitchStatus, fixedStep, coroutineScope, prewarmer, assetPrewarmer)

    /**
     * These services, with [fixedStep] reporting the fixed-step loop the host steps the systems in, as
     * `runProject` does with the scene's own. A game server or a test that runs [SceneSystemSet.fixed]
     * from its own [com.awakekt.awake.core.host.FixedTimestepLoop] passes that loop.
     */
    fun withFixedStep(fixedStep: FixedStepReport): SceneHostServices =
        SceneHostServices(gpu, input, physics, content, spawner, audio, sceneSwitch, sceneSwitchStatus, fixedStep, coroutineScope, prewarmer, assetPrewarmer)

    /**
     * These services, with the scene's systems running their background work in children of [scope],
     * as `runProject` does with the scene session's scope. A host that runs the systems itself passes
     * its session's scope, or another it cancels when the scene goes away.
     */
    fun withCoroutineScope(scope: CoroutineScope): SceneHostServices =
        SceneHostServices(gpu, input, physics, content, spawner, audio, sceneSwitch, sceneSwitchStatus, fixedStep, scope, prewarmer, assetPrewarmer)

    /**
     * A child of [coroutineScope] for one system's background work, which the system cancels when it
     * closes. When the host gave no scope, a child of a scope nobody owns, kept for hosts built before
     * [withCoroutineScope]; a warning names [what] once.
     *
     * @param what The system asking, for the warning.
     */
    fun backgroundScope(what: String): CoroutineScope = ownerScope(what).childScope()

    /** [coroutineScope], or for a host that gave none, the unowned compatibility scope. */
    internal fun ownerScope(what: String): CoroutineScope = coroutineScope ?: HostlessSceneScope.forHostWithoutScope(what)

    /** Makes services for a host with no renderer. */
    companion object {
        /**
         * Services for a host that doesn't draw, such as a game server or a test: the scene's systems
         * that simulate run, and the ones that draw (particle sprites, shader effects) are left out.
         */
        fun headless(
            input: () -> GameplayInput,
            physics: PhysicsWorld? = null,
            content: SceneContent = SceneContent.Empty,
        ): SceneHostServices = SceneHostServices(null, input, physics, content)
    }
}

/**
 * The systems a scene plays with, in the order they run: every [fixed] system on each fixed step,
 * then [interpolate], then every [preAnimation] system once per rendered frame before world matrices
 * are propagated, and then every [frame] system once per rendered frame, which is the order
 * `runProject` runs them in. [close] when the scene stops, to release what the systems created.
 */
class SceneSystemSet internal constructor(
    /** The systems run on each fixed simulation step, in capability order. */
    val fixed: List<System>,
    /** The systems run once per rendered frame after world matrices are propagated. */
    val frame: List<System>,
    private val release: () -> Unit,
    /** The systems run once per rendered frame after [interpolate] and before world-matrix propagation. */
    val preAnimation: List<System> = emptyList(),
    /**
     * The systems that were planned, with their names and phases, in the order they run: [fixed], then
     * [preAnimation], then [frame]. For diagnostics and tests. Empty for a set built by hand.
     */
    val planned: List<PlannedSystem> = emptyList(),
) : AutoCloseable {
    private val interpolated = fixed.filterIsInstance<InterpolatedSystem>()

    /**
     * Blends the last two states of every [fixed] system that keeps them, as
     * [InterpolatedSystem.interpolate] describes, by the [alpha] the host's fixed-step loop reports.
     * Call it after the fixed steps and before [preAnimation] and [frame] systems, which read what it
     * places, as `runProject` does; without it, bodies move in fixed-step jumps.
     */
    fun interpolate(world: World, alpha: Float) {
        // By index: forEach takes an iterator, a small allocation every frame.
        for (index in interpolated.indices) interpolated[index].interpolate(world, alpha)
    }

    override fun close() = release()
}

/**
 * The systems [scene] runs, and none it doesn't, built from [services]: Core's capabilities first, then
 * [capabilities] in order, each adding what [scene] uses of it. A host that runs a scene in a world of
 * its own, as an editor's Play does, runs these instead of keeping a list of its own, so it gains a
 * system when Core does:
 * - `movement_control`, `input_actions` or a `canvas_element` with an action: the input actions, read
 *   from the keys and pointer buttons; a `movement_control` follows them, moved by physics when the
 *   entity has a `character_controller` and straight through the world when it doesn't
 * - `physics_body`, `character_controller` and a `terrain` collider: the physics step, the
 *   character controller and the terrain collider, when [SceneHostServices.physics] is given; a `mesh`
 *   collision shape adds [com.awakekt.awake.scene.physics.MeshColliderSystem], which reads the
 *   triangles from [SceneHostServices.content]
 * - `camera_rig`: the camera system
 * - `terrain`: drawn through [SceneHostServices.renderer] when it takes content features, with the
 *   surface providers in [SceneHostServices.content]
 * - `spinControl`: spinning
 * - `locomotion_animation` and `keyframe_animation`: their clips and looping tracks
 * - `texture_clips`: the sprite sheet's clips, stepped on the scene's clock
 * - `sprite_clips`: [com.awakekt.awake.scene.scene2d.SpriteClipSystem], which steps each sprite's clip
 *   on the scene's clock and shows its frame. `runProject` leaves it out when the app's infrastructure
 *   already runs one over the same world, as `defaultInfrastructureSystems` does, so a clip is not
 *   advanced twice a frame
 * - `particle_emitter`: its emitters, with the sprites in [SceneHostServices.content]
 * - `shader_effect`: its documents, drawn through [SceneHostServices.renderer] when it takes content
 *   features
 * - `day_cycle`: the sun's path and the blended sky, light and fog
 * - `paged_terrain`: its cells streamed around the primary camera from the index and shared images in
 *   [SceneHostServices.content], drawn through [SceneHostServices.renderer] when it takes content
 *   features, with collision cells when it is a `collider` and [SceneHostServices.physics] is given
 * - canvas input watchers, always: controls added at runtime get [CanvasActionSystem], which adds
 *   their input actions and turns the camera from a look area; an idle scene creates no input state
 * - `patrol`, `chase` and `flee`, with a `navigation` component to route them over: the behaviours and
 *   the system that answers their route requests
 * - skinned glTF animation, always
 *
 * Pass the same [capabilities] to [loadSceneContent] for the content. `runProject` is built on the same
 * decision, so the two cannot drift apart. These are only the scene's own systems: the host still
 * places the scene, resolves its assets, picks the camera, resolves transforms and draws. A scene that
 * needs physics with no [SceneHostServices.physics] runs without those systems.
 */
fun sceneSystemsFor(
    scene: SceneDocument,
    services: SceneHostServices,
    capabilities: List<SceneCapability> = emptyList(),
): SceneSystemSet = sceneSystemSetFor(scene, services, capabilities, disabled = emptySet(), hostRuns = emptySet())

/**
 * [sceneSystemsFor], leaving out the systems named in [disabled], such as `CoreSystems.CAMERA` on a
 * dedicated server. A name no system has is refused, with the names that exist: Core's in
 * [CoreSystems.all], those [SceneCapability.systemNames] lists, and any [SceneSystemSet.planned] for
 * this scene. Nothing disabled plans exactly what [sceneSystemsFor] does without it.
 */
fun sceneSystemsFor(
    scene: SceneDocument,
    services: SceneHostServices,
    capabilities: List<SceneCapability>,
    disabled: Set<String>,
): SceneSystemSet = sceneSystemSetFor(scene, services, capabilities, disabled, hostRuns = emptySet())

/**
 * The decision both [sceneSystemsFor] and `runProject` build from. [disabled] is what a host or consumer
 * asked to drop and is refused when no system has the name. [hostRuns] are the systems the host already
 * runs over the same world, such as the infrastructure's sprite clips: they are dropped the same way,
 * and are not an error where the scene plans none of them.
 */
internal fun sceneSystemSetFor(
    scene: SceneDocument,
    services: SceneHostServices,
    capabilities: List<SceneCapability>,
    disabled: Set<String>,
    hostRuns: Set<String>,
): SceneSystemSet {
    val fixed = mutableListOf<System>()
    val preAnimation = mutableListOf<System>()
    val frame = mutableListOf<System>()
    val releasing = SystemReleases()
    val specs = sceneSystemSpecsFor(scene, hasPhysics = services.physics != null, capabilities, hasRenderer = services.hasRenderer)
    val installed = installedCapabilities(capabilities)
    requireKnownSystemNames(disabled, CoreSystems.all + installed.flatMap { it.systemNames } + specs.map { it.name }, "the scene's systems")
    val dropped = disabled + hostRuns
    val kept = specs.filterNot { it.name in dropped }
    kept.forEach { spec ->
        val system = releasing.keep(spec.create(services))
        when (spec.phase) {
            SceneSystemPhase.Fixed -> fixed += system
            SceneSystemPhase.PreAnimation -> preAnimation += system
            SceneSystemPhase.Frame -> frame += system
        }
    }
    val planned = listOf(SceneSystemPhase.Fixed, SceneSystemPhase.PreAnimation, SceneSystemPhase.Frame)
        .flatMap { phase -> kept.filter { it.phase == phase }.map { PlannedSystem(it.name, phase) } }
    return SceneSystemSet(fixed, frame, releasing::release, preAnimation, planned)
}

/** One system a scene runs: its name, the phase it runs in, and how to build it. */
internal class SceneSystemSpec(
    val name: String,
    val phase: SceneSystemPhase,
    val create: (SceneHostServices) -> System,
)

/**
 * The decision, as data: which systems [scene] needs, in the order they run, from Core's capabilities
 * and then [capabilities]. Both [sceneSystemsFor] and `runProject` build from this list, so there is
 * one place that says what a component needs.
 */
internal fun sceneSystemSpecsFor(
    scene: SceneDocument,
    hasPhysics: Boolean,
    capabilities: List<SceneCapability> = emptyList(),
    hasRenderer: Boolean = true,
): List<SceneSystemSpec> {
    val plan = SceneSystemPlan(hasPhysics, hasRenderer)
    installedCapabilities(capabilities).forEach { it.plan(scene, plan) }
    return plan.specs
}

/** Core's capabilities followed by [extra], refusing two with one id. */
internal fun installedCapabilities(extra: List<SceneCapability>): List<SceneCapability> {
    val installed = CORE_CAPABILITIES + extra
    val repeated = installed.groupBy { it.id }.filterValues { it.size > 1 }.keys
    require(repeated.isEmpty()) { "More than one capability has the id ${repeated.joinToString()}" }
    return installed
}

/** The systems that hold something to give back, closed newest first when the scene stops. */
private class SystemReleases {
    private val closing = mutableListOf<AutoCloseable>()

    fun keep(system: System): System = system.also { if (it is AutoCloseable) closing += it }

    fun release() {
        closing.asReversed().forEach(AutoCloseable::close)
        closing.clear()
    }
}
