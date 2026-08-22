// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.scene.runtime

import io.github.ronjunevaldoz.awake.core.host.FixedTimestepLoop
import io.github.ronjunevaldoz.awake.core.input.Input
import io.github.ronjunevaldoz.awake.core.input.Key
import io.github.ronjunevaldoz.awake.ecs.Entity
import io.github.ronjunevaldoz.awake.ecs.System
import io.github.ronjunevaldoz.awake.ecs.World
import io.github.ronjunevaldoz.awake.engine.platform.dsl.AppServiceLookup
import io.github.ronjunevaldoz.awake.engine.platform.lifecycle.AppFrame
import io.github.ronjunevaldoz.awake.engine.platform.lifecycle.AppLifecycle
import io.github.ronjunevaldoz.awake.render.material.Material
import io.github.ronjunevaldoz.awake.render.mesh.Mesh
import io.github.ronjunevaldoz.awake.render.renderer.DrawCall
import io.github.ronjunevaldoz.awake.render.renderer.Renderer
import io.github.ronjunevaldoz.awake.render.texture.TextureAsset
import io.github.ronjunevaldoz.awake.scene.core.components.Name
import io.github.ronjunevaldoz.awake.scene.core.components.Transform
import io.github.ronjunevaldoz.awake.scene.core.systems.TransformSystem
import io.github.ronjunevaldoz.awake.scene.rendering.components.Camera
import io.github.ronjunevaldoz.awake.scene.rendering.components.InstancedMeshRenderer
import io.github.ronjunevaldoz.awake.scene.rendering.components.InstancedSkinnedMeshRenderer
import io.github.ronjunevaldoz.awake.scene.rendering.components.MeshRenderer
import io.github.ronjunevaldoz.awake.scene.rendering.systems.DebugVisualizationSystem
import io.github.ronjunevaldoz.awake.scene.rendering.systems.RenderSystem
import io.github.ronjunevaldoz.awake.ui.UiInputState
import io.github.ronjunevaldoz.awake.ui.context.UiContext
import io.github.ronjunevaldoz.awake.ui.context.UiCursor
import io.github.ronjunevaldoz.awake.ui.context.UiFrameInput
import io.github.ronjunevaldoz.awake.ui.context.UiMeasureTrialStats
import io.github.ronjunevaldoz.awake.ui.font.UiFont
import io.github.ronjunevaldoz.awake.ui.font.UiFonts
import io.github.ronjunevaldoz.awake.ui.toUiInputState
import io.github.ronjunevaldoz.awake.core.math.Lens
import kotlin.time.TimeSource

/**
 * Orchestrates a single 3D scene session.
 */
class SceneAppLifecycleRuntime internal constructor(
    val spec: SceneAppSpec,
) : AppLifecycle {
    private val fixedTimestepLoop = FixedTimestepLoop()

    /** All systems indexed by their handle. */
    private val registeredSystems = linkedMapOf<SceneSystemHandle<out System>, System>()

    /** Systems that run during fixed-timestep simulation steps. */
    private val fixedSystems = mutableListOf<System>()

    /** Systems that run once per rendered frame. */
    private val frameSystems = mutableListOf<System>()

    lateinit var world: World
        private set
    lateinit var renderer: Renderer
        private set

    /** The single owner of "what scene is currently loaded" -- see [SceneManager]'s own doc
     * comment. Lazy, not eager: [world] is `lateinit`, set later than this property's
     * declaration, and lazy defers construction until first access instead of at field-init
     * time. */
    val sceneManager: SceneManager by lazy { SceneManager(world) }

    private var assetLibrary: SceneAssetLibrary? = null

    /** Mandatory, not user-configurable -- see [SceneAppSpec.infrastructureSystemsFactory]. */
    private lateinit var infrastructureSystems: List<System>

    val uiContext = UiContext()
    val font: UiFont = UiFonts.default()

    /** The overlay's cursor request for the current frame -- a desktop host applies it by
     * passing `cursor = { runtime.cursor }` to `runVulkanDesktopGame` (see AppUiRuntime.cursor,
     * the same opt-in shape). Dropped on the floor before this existed: resize handles and text
     * fields requested cursors that no platform call ever consumed. */
    var cursor: UiCursor = UiCursor.Default
        private set

    /** Rolling window backing [averageFrameTimeMs]/[fps] -- same shape as
     * `AppUiRuntime.recordFrameTime`'s own tracker (see `SceneAppFrame.kt`'s `frameStats()`
     * port, which reads these). */
    private val frameTimesMs = ArrayDeque<Float>()

    private fun recordFrameTime(deltaSeconds: Float) {
        frameTimesMs.addLast(deltaSeconds * 1000f)
        while (frameTimesMs.size > FRAME_TIME_HISTORY_SIZE) frameTimesMs.removeFirst()
    }

    /**
     * Splits the frame into its three phases so a slow frame can be attributed instead of
     * guessed at. Off by default: arming it also arms [UiMeasureTrialStats]'s per-trial timing,
     * which is only free while disabled. F2 toggles it (F3 and F5 are browser Find and reload,
     * and web is where the split matters most).
     *
     * Each phase is stored from the *previous* frame, because the overlay that displays them is
     * itself drawn inside phase 1 -- reading a live counter there would report a partial frame.
     */
    var perfStatsEnabled: Boolean = false
    private var uiBuildMs = 0f
    private var uiStageMs = 0f
    private var simRenderMs = 0f
    private var trialMs = 0f
    private var trialPasses = 0

    /** Zeroed while disabled rather than left holding the last armed frame -- a stale number
     * displayed next to a live one reads as live. */
    fun phaseStats(): ScenePhaseStats =
        if (perfStatsEnabled) ScenePhaseStats(uiBuildMs, uiStageMs, simRenderMs, trialMs, trialPasses)
        else ScenePhaseStats()

    private inline fun <T> timePhase(record: (Float) -> Unit, block: () -> T): T {
        if (!perfStatsEnabled) return block()
        val start = TimeSource.Monotonic.markNow()
        try {
            return block()
        } finally {
            record(start.elapsedNow().inWholeMicroseconds / 1000f)
        }
    }

    val averageFrameTimeMs: Float
        get() = if (frameTimesMs.isEmpty()) 0f else frameTimesMs.sum() / frameTimesMs.size

    val fps: Float
        get() = averageFrameTimeMs.takeIf { it > 0f }?.let { 1000f / it } ?: 0f

    val sceneName: String
        get() = spec.sceneName ?: "scene"

    private lateinit var services: AppServiceLookup

    /** The session's input accumulator, for writing focus state back at the end of a frame.
     * Reading this frame's input goes through [AppFrame.input] instead. Resolved once, not per
     * frame -- [services] is only set in [initialize], hence `lazy`. */
    private val input: Input by lazy { services.requireService(Input::class) }

    /** Called at install time (see [SceneAppSpec.installInto]). */
    fun initialize(services: AppServiceLookup) {
        this.services = services
        this.world = World()
        this.assetLibrary = spec.assetLibraryFactory?.invoke()
    }

    override suspend fun ready(renderer: Renderer) {
        this.renderer = renderer
        infrastructureSystems = spec.infrastructureSystemsFactory(this)

        // 1. Instantiate systems (after renderer is available)
        spec.systems.forEach { registration ->
            val system = registration.factory(this)
            registeredSystems[registration.handle] = system

            // Sort into execution buckets owned by the scene runtime, not the system object.
            when (registration.phase) {
                SceneSystemPhase.Fixed -> fixedSystems.add(system)
                SceneSystemPhase.Frame -> frameSystems.add(system)
            }
        }

        // 2. RUN THE SCENE BUILDER BLUEPRINT (Delayed Execution)
        spec.scenePopulationBlock(this)

        // 3. Asset loading before first system tick so initial frame update has resources available.
        spec.onReadyBlock(this)

        // 4. Initial sync pass for all frame-rate systems, infrastructure (transform
        // resolution + the draw pass) always last so it sees this frame's final state.
        frameSystems.forEach { it.update(world, 0f) }
        infrastructureSystems.forEach { it.update(world, 0f) }
    }

    override fun update(frame: AppFrame) {
        val delta = frame.delta
        val viewportWidth = frame.viewportWidth
        val viewportHeight = frame.viewportHeight
        val snapshot = frame.input
        recordFrameTime(delta)

        if (snapshot.wasPressed(Key.F2)) perfStatsEnabled = !perfStatsEnabled
        // Opt-in per-trial timing, same zero-cost-when-disabled contract AppUiRuntime uses.
        UiMeasureTrialStats.enabled = perfStatsEnabled
        if (perfStatsEnabled) UiMeasureTrialStats.reset()

        // 1. UI Pass
        val uiFrame = timePhase({ uiBuildMs = it }) {
            uiContext.beginFrame(
                UiFrameInput(
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    input = snapshot.toUiInputState(),
                    deltaSeconds = delta,
                ),
            )
            spec.overlayBlock(this, viewportWidth, viewportHeight)
            uiContext.finishFrame()
        }
        if (perfStatsEnabled) {
            // Snapshotted here, not read live by the overlay: the overlay draws inside phase 1
            // above, so a live read would report only the trials that ran before it.
            trialMs = UiMeasureTrialStats.trialNanos / NANOS_PER_MS
            trialPasses = UiMeasureTrialStats.trialCount
        }

        // 2. Stage UI before the infrastructure render pass. Vulkan/WebGPU consume these
        // runs while recording the present-producing renderer.draw() call below; staging
        // after RenderSystem would always draw the previous frame's overlay.
        timePhase({ uiStageMs = it }) {
            renderer.drawUi(uiFrame.primitives, font)
        }

        // 3. Simulation & Infrastructure Pump
        timePhase({ simRenderMs = it }) {
            fixedTimestepLoop.advance(
                frameDelta = delta,
                fixedUpdate = { step ->
                    fixedSystems.forEach { it.update(world, step) }
                    spec.updateBlock(this, step, snapshot)
                },
                render = {
                    frameSystems.forEach { it.update(world, delta) }
                    infrastructureSystems.forEach { it.update(world, delta) }
                },
            )
        }

        // 4. Sync UI focus state back to session input.
        input.textInputFocused = uiFrame.effects.requestKeyboard
        cursor = uiFrame.effects.cursor
    }

    override fun resize(width: Float, height: Float) = Unit

    override fun dispose() {
        spec.onDisposeBlock(this)
        sceneManager.close()
        assetLibrary?.dispose()
        assetLibrary = null
        registeredSystems.clear()
        fixedSystems.clear()
        frameSystems.clear()
    }

    fun requireAssetLibrary(): SceneAssetLibrary = checkNotNull(assetLibrary) {
        "No scene asset library is registered for '$sceneName'."
    }

    fun requireMesh(name: String): Mesh = requireAssetLibrary().requireMesh(this, name)

    fun requireMaterial(name: String): Material = requireAssetLibrary().requireMaterial(this, name)

    suspend fun readback(camera: Lens, width: Int, height: Int): TextureAsset {
        val target = renderer.createRenderTarget(width, height)
        return try {
            renderer.renderToTexture(target, camera, collectDrawCalls())
            renderer.readPixels(target)
        } finally {
            target.destroy()
        }
    }

    /** Every drawable entity, generic across ordinary/instanced/skinned-instanced content --
     * mirrors [RenderSystem.update]'s own draw-call assembly (minus its LOD/culling/frustum
     * concerns, not needed for an offscreen preview pass). Originally only queried plain
     * [MeshRenderer] entities, which left [InstancedMeshRenderer]/[InstancedSkinnedMeshRenderer]
     * content (instanced-cubes, instanced-skinned) invisible to any caller of this function
     * (the camera preview / orientation gizmo's offscreen passes) even though the real
     * viewport renders them fine via [RenderSystem]. */
    fun collectDrawCalls(): List<DrawCall> {
        val family = world.family<Transform, MeshRenderer>()
        val transforms = family.componentsA()
        val renderers = family.componentsB()
        return buildList(family.size) {
            var index = 0
            while (index < family.size) {
                add(
                    DrawCall(
                        mesh = renderers[index].mesh,
                        material = renderers[index].material,
                        model = transforms[index].worldMatrix,
                    ),
                )
                index += 1
            }
            world.family<InstancedMeshRenderer>().forEach { _, instanced ->
                add(
                    DrawCall(
                        mesh = instanced.mesh,
                        material = instanced.material,
                        instanceModels = instanced.transforms,
                    ),
                )
            }
            world.family<InstancedSkinnedMeshRenderer>().forEach { _, instanced ->
                add(
                    DrawCall(
                        mesh = instanced.mesh,
                        material = instanced.material,
                        instanceModels = instanced.instances.map { it.transform },
                        instanceJointPalettes = instanced.instances.map { it.jointPalette },
                    ),
                )
            }
        }
    }

    fun <T : Any> service(type: kotlin.reflect.KClass<T>): T? = services.service(type)

    fun <T : Any> requireService(type: kotlin.reflect.KClass<T>): T = services.requireService(type)

    // registeredSystems is keyed by SceneSystemHandle<T>, and only ever populated (in ready())
    // with the System instance that handle's own factory produced, so `as? T` matches or the
    // error() below fires -- it never silently returns a mistyped system.
    @Suppress("UNCHECKED_CAST")
    fun <T : System> system(handle: SceneSystemHandle<T>): T =
        registeredSystems[handle] as? T ?: error("System ${handle.name} not found")

    fun system(name: String): System =
        registeredSystems.entries.firstOrNull { it.key.name == name }?.value
            ?: error("System $name not found")

    fun <T : System> update(handle: SceneSystemHandle<T>, delta: Float) {
        val system = system(handle)
        system.update(world, delta)
    }

    fun findEntity(name: String): Entity? {
        var result: Entity? = null
        world.queryEach(Name::class) { entity, n ->
            if (n.value == name) result = entity
        }
        return result
    }

    fun requireEntity(name: String): Entity =
        findEntity(name) ?: error("Entity with name '$name' not found")

    fun findTransform(name: String): Transform? =
        findEntity(name)?.let { world.get(it, Transform::class) }

    fun requireTransform(name: String): Transform = world.get(requireEntity(name), Transform::class)
        ?: error("Entity '$name' has no Transform component")

    fun findCamera(name: String): Camera? = findEntity(name)?.let { world.get(it, Camera::class) }

    fun requireCamera(name: String): Camera = world.get(requireEntity(name), Camera::class)
        ?: error("Entity '$name' has no Camera component")

    val inputState: UiInputState get() = uiContext.inputState

    private companion object {
        const val FRAME_TIME_HISTORY_SIZE = 30
        const val NANOS_PER_MS = 1_000_000f
    }
}

/** The standard infrastructure trio every 3D scene needs -- transform resolution, the draw
 * pass, then debug wireframes (frustum/[io.github.ronjunevaldoz.awake.scene.rendering
 * .components.MeshBounds] boxes) drawn over whatever [RenderSystem] just drew. See
 * [SceneAppSpec.infrastructureSystemsFactory]'s doc comment for why this lives here instead
 * of being wired through the authoring DSL. [DebugVisualizationSystem] is a no-op (draws
 * nothing extra) unless a scene adds a
 * [io.github.ronjunevaldoz.awake.scene.rendering.components.WorldDebugSettings] entity and
 * toggles it on -- every existing scene is unaffected by its presence here. */
fun SceneAppLifecycleRuntime.defaultInfrastructureSystems(): List<System> =
    listOf(TransformSystem(), RenderSystem(renderer), DebugVisualizationSystem(renderer))
