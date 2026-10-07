/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.compose.ComposeAppRuntime
import com.awakekt.awake.engine.compose.GraphicsLayerCompositor
import com.awakekt.awake.engine.platform.core.FrameStats
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.engine.platform.lifecycle.AppLifecycle
import com.awakekt.awake.render.capture.FramebufferAttachment
import com.awakekt.awake.render.capture.FramebufferAttachmentData
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.sprites.SpriteRenderBatch
import com.awakekt.awake.render.renderer.RenderFrameStats
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.canvas.CanvasElement
import com.awakekt.awake.scene.canvas.SceneCanvas
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.debug.DebugVisualizationSystem
import com.awakekt.awake.scene.runtime.session.SceneSession
import com.awakekt.awake.scene.scene2d.SpriteClipSystem
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * Orchestrates a single 3D scene session.
 *
 * @property spec The specification defining the lifecycle, systems, and content for this scene.
 */
class SceneAppLifecycleRuntime internal constructor(
    val spec: SceneAppSpec,
) : AppLifecycle {
    /** The active [SceneSession] orchestrating ECS state and scheduling for this runtime. */
    val session = SceneSession(spec)

    /** The active ECS [World] instance. */
    val world: World
        get() = session.world

    /** The active [Renderer] instance initialized at backend readiness. */
    lateinit var renderer: Renderer
        private set

    /** Resolver captured once at backend readiness and injected into the scene render system. */
    internal var gpuDrawPreparer: GpuDrawPreparer? = null

    /** The [SceneManager] coordinating scene hierarchy and document transitions. */
    val sceneManager: SceneManager
        get() = session.sceneManager

    /** Compatibility-only host for the legacy `content {}` DSL. New applications install an
     * app-level Compose module instead. */
    val uiHost: ComposeHost by lazy(::ComposeHost)
    private val graphicsLayers = GraphicsLayerCompositor()

    /** Default font used for UI and debug text rendering. */
    val font: UiFont = UiFonts.default()

    /** The UI's cursor request for the current frame -- a desktop host applies it by
     * passing `cursor = { runtime.cursor }` to `runVulkanDesktopGame`. Dropped on the floor
     * before this existed: resize handles and text fields requested cursors that no platform
     * call ever consumed. */
    var cursor: PointerCursor = PointerCursor.Default
        private set

    /**
     * What the UI claimed from the last frame's input.
     *
     * Read by the camera and player systems, which previously called `uiContext.finishFrame()` a
     * *second* time mid-frame to obtain it -- finishing a frame to ask a question about it. Held
     * here instead, so the frame is finished once and its answer is available to whoever runs next.
     */
    var uiOwnership: InputOwnership = InputOwnership()

    /**
     * Last frame's accessibility tree -- how a host or a test addresses a node the UI placed.
     *
     * Empty when a scene declares no `content { }` at all.
     */
    var uiSemantics: List<SemanticsNode> = emptyList()
        private set

    /** Whether the scene's touch-only canvas controls are drawn, for a host on a touch screen. */
    var showTouchControls: Boolean = false

    /**
     * Whether this runtime draws the scene's canvas over its whole UI. A host that shows the game in
     * part of its window, such as an editor's game view, turns this off and draws `SceneCanvas`
     * there itself.
     */
    var drawsSceneCanvas: Boolean = true

    /** Last frame's draw commands, for a test that has to assert on what was painted. */
    var uiPrimitives: List<UiDrawPrimitive> = emptyList()
        private set

    /** Backend-neutral 2D primitives staged by scene systems for this frame's UI composite. */
    private val stagedUiPrimitives = ArrayList<UiDrawPrimitive>()

    /**
     * Adds screen-space primitives to the current frame's UI overlay.
     *
     * Scene systems run before the runtime composites UI, so this is the explicit lifecycle seam
     * for demo render coordinators and future reusable 2D systems. It keeps them independent of a
     * backend renderer while avoiding a one-frame delay.
     */
    fun stageUi(primitives: List<UiDrawPrimitive>) {
        stagedUiPrimitives += primitives
    }

    /** Short rolling window backing [averageFrameTimeMs]/[fps] -- see `SceneAppFrame.kt`'s
     * `frameStats()`, which reads these. */
    private val recentFrames = FrameStats(percentileWindowSize = FRAME_TIME_HISTORY_SIZE)

    /** A few seconds of frames, long enough that one stall stays visible in its p99 and max. */
    internal val frameSpread = FrameStats(percentileWindowSize = FRAME_SPREAD_HISTORY_SIZE)

    /** The renderer's last frame, or null before [ready] hands this runtime one. */
    internal val rendererFrameStats: RenderFrameStats?
        get() = if (::renderer.isInitialized) renderer.frameStats else null

    private fun recordFrameTime(deltaSeconds: Float) {
        recentFrames.update(deltaSeconds)
        frameSpread.update(deltaSeconds)
    }

    /**
     * Splits the frame into its phases so a slow frame can be attributed instead of
     * guessed at. F2 toggles it (F3 and F5 are browser Find and reload, and web is where the split
     * matters most). It no longer arms anything: `ui-core`'s per-trial timing was the expensive
     * half, and the compose engine has no trial passes to time.
     *
     * Each phase is stored from the *previous* frame, because the overlay that displays them is
     * itself drawn inside phase 1 -- reading a live counter there would report a partial frame.
     */
    var perfStatsEnabled: Boolean = false
    private var uiBuildMs = 0f
    private var uiWaitMs = 0f
    private var uiStageMs = 0f
    private var simRenderMs = 0f

    /** Zeroed while disabled rather than left holding the last armed frame -- a stale number
     * displayed next to a live one reads as live. */
    fun phaseStats(): ScenePhaseStats =
        if (perfStatsEnabled) {
            val schedule = session.schedule
            ScenePhaseStats(uiBuildMs, uiWaitMs, uiStageMs, simRenderMs, gameMs = schedule.gameMs, renderMs = schedule.renderMs)
        } else {
            ScenePhaseStats()
        }

    private inline fun <T> timePhase(record: (Float) -> Unit, block: () -> T): T {
        if (!perfStatsEnabled) return block()
        val start = TimeSource.Monotonic.markNow()
        try {
            return block()
        } finally {
            record(start.elapsedNow().inWholeNanoseconds / 1_000_000f)
        }
    }

    /** The running average frame time in milliseconds over the recent sampling window. */
    val averageFrameTimeMs: Float
        get() = recentFrames.averageFrameTimeMs

    /** The estimated current frames per second based on [averageFrameTimeMs]. */
    val fps: Float
        get() = averageFrameTimeMs.takeIf { it > 0f }?.let { 1000f / it } ?: 0f

    /** The descriptive name of the scene, or `"scene"` if unnamed. */
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
        // Install built-in scene component resolvers (Camera, Light, PbrMaterial, SpinControl,
        // MeshRenderer, PrefabLink) into the global SceneComponentRegistry. Explicit here
        // rather than in DefaultSceneComponentResolvers.init{} so tests can run without
        // triggering global state, and so subclasses can opt out or replace the set.
        DefaultSceneComponentResolvers.install()
        session.initialize()
    }

    override suspend fun ready(renderer: Renderer) {
        this.renderer = renderer
        gpuDrawPreparer = (renderer as? GpuDrawPreparationSource)?.gpuDrawPreparer
        session.ready(this)
    }

    override fun update(frame: AppFrame) {
        val delta = frame.delta
        val viewportWidth = frame.viewportWidth
        val viewportHeight = frame.viewportHeight
        val snapshot = frame.input
        recordFrameTime(delta)
        // Before anything this frame can log, so every record it produces carries this frame's
        // number rather than the previous one. The runtime is the only thing that knows where a
        // frame begins, which is why it owns the counter rather than each logging call site.
        Log.advanceFrame()
        if (snapshot.wasPressed(Key.F2)) perfStatsEnabled = !perfStatsEnabled

        stagedUiPrimitives.clear()

        // 1. Simulation & infrastructure pump. Rendering systems stage the scene and any
        // backend-neutral 2D overlay before the runtime composites the frame below.
        session.schedule.timed = perfStatsEnabled
        timePhase({ simRenderMs = it }) {
            session.advance(delta) { step ->
                spec.updateBlock(this, step, snapshot)
            }
        }

        // A scene's own canvas elements need the UI frame even when the app declares no `ui { }`.
        val uiFrame = (spec.ui ?: SceneCanvasOnly.takeIf { world.family<CanvasElement>().size > 0 })?.let {
            // The backend only knows the real display scale once its window exists, which is after
            // `uiHost` was constructed -- see ComposeHost.density's own doc comment for why this is
            // a live per-frame assignment rather than a constructor argument.
            uiHost.density = frame.density
            timePhase({ uiBuildMs = it }) {
                buildUiFrame(snapshot, delta, viewportWidth, viewportHeight)
            }.also { builtFrame ->
                // Timed on its own, before staging. `drawUi` waits on this fence anyway; taking it
                // here first costs nothing and stops a GPU-bound frame from reading as an
                // expensive UI. See Renderer.awaitFrameResources.
                timePhase({ uiWaitMs = it }) { renderer.awaitFrameResources() }
                timePhase({ uiStageMs = it }) {
                    renderer.drawUi(
                        graphicsLayers.composite(
                            renderer = renderer,
                            primitives = stagedUiPrimitives + builtFrame.primitives,
                            layers = builtFrame.graphicsLayers,
                            font = font,
                            viewportWidth = viewportWidth.toInt(),
                            viewportHeight = viewportHeight.toInt(),
                        ),
                        font,
                    )
                }
            }
        }

        if (uiFrame != null) {
            input.textInputFocused = uiFrame.requestKeyboard
            input.textInputPassword = uiFrame.passwordKeyboard
            uiFrame.clipboardText?.let { input.clipboardWrite = it }
            cursor = uiFrame.cursor
            uiOwnership = uiFrame.ownership
            uiSemantics = uiFrame.semantics
            uiPrimitives = stagedUiPrimitives + uiFrame.primitives
        } else {
            val composeRuntime = services.service(ComposeAppRuntime::class)
            if (composeRuntime != null) {
                uiOwnership = composeRuntime.inputOwnership
                cursor = composeRuntime.cursor
                composeRuntime.lastFrame?.let { last ->
                    uiSemantics = last.semantics
                    uiPrimitives = last.primitives
                }
            }
        }

        if (uiFrame == null && stagedUiPrimitives.isNotEmpty()) {
            timePhase({ uiWaitMs = it }) { renderer.awaitFrameResources() }
            timePhase({ uiStageMs = it }) {
                renderer.drawUi(stagedUiPrimitives.toList(), font)
            }
            uiPrimitives = stagedUiPrimitives.toList()
        }
    }

    /** Runs the scene's declared UI, or an empty frame if it draws none. */
    private fun buildUiFrame(
        snapshot: InputSnapshot,
        delta: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ): SceneFrame {
        val content = spec.ui
        val hasCanvas = drawsSceneCanvas && world.family<CanvasElement>().size > 0
        if (content == null && !hasCanvas) {
            return SceneFrame(emptyList(), emptyList(), emptyList(), InputOwnership(), PointerCursor.Default, false)
        }
        val frame = uiHost.frame(
            snapshot.toFrameInput(viewportWidth.roundToInt(), viewportHeight.roundToInt(), delta),
        ) {
            // Provided, not threaded: content reads what it needs instead of every composable
            // beneath it carrying the same services down.
            CompositionLocalProvider(
                LocalWorld provides world,
                LocalRenderer provides renderer,
                LocalFrameStats provides frameStats(),
            ) {
                // Under the app's own UI, so a menu or pause screen covers the game's canvas.
                if (hasCanvas) SceneCanvas(world, showTouchControls = showTouchControls)
                if (content != null) content()
            }
        }
        return SceneFrame(
            frame.primitives,
            frame.graphicsLayers,
            frame.semantics,
            frame.ownership,
            frame.effects.cursor,
            frame.effects.requestKeyboard,
            frame.effects.passwordKeyboard,
            frame.effects.clipboardText,
        )
    }

    override fun resize(width: Float, height: Float) = Unit

    internal val spriteBatch by lazy { SpriteRenderBatch(renderer) { requireAssetLibrary().requireTexture(it) } }
    internal val spriteFeature by lazy { SceneSpriteRenderFeature(spriteBatch) }
    internal val tilemapFeature by lazy { SceneTilemapRenderFeature(renderer) { requireAssetLibrary().requireTexture(it) } }

    override fun dispose() {
        if (::renderer.isInitialized) {
            spriteBatch.destroy()
            tilemapFeature.destroy()
        }
        graphicsLayers.dispose()
        session.dispose(this)
    }

    /**
     * Obtains the active [SceneAssetLibrary], throwing [IllegalStateException] if none was configured.
     *
     * @return The active [SceneAssetLibrary] instance.
     */
    fun requireAssetLibrary(): SceneAssetLibrary = session.requireAssetLibrary()

    /**
     * Resolves and returns a named [Mesh] from the scene asset library.
     *
     * @param name The asset key or identifier of the mesh.
     * @return The loaded [Mesh] instance.
     */
    fun requireMesh(name: String): Mesh = session.requireMesh(this, name)

    /**
     * Resolves and returns a named [Material] from the scene asset library.
     *
     * @param name The asset key or identifier of the material.
     * @return The loaded [Material] instance.
     */
    fun requireMaterial(name: String): Material = session.requireMaterial(this, name)

    /** The scene as [camera] sees it, drawn offscreen at [width] by [height] the way the frame draws it. */
    suspend fun readback(camera: Lens, width: Int, height: Int): TextureAsset {
        val target = renderer.createRenderTarget(width, height)
        return try {
            renderer.renderToTexture(target, planCapture(Camera(camera), width, height))
            renderer.readPixels(target)
        } finally {
            target.destroy()
        }
    }

    /**
     * Captures one attachment from the scene's current primary-camera view.
     *
     * This is the runtime seam for tools such as the showcase framebuffer debugger. The scene
     * owns camera selection and draw-call collection; callers only choose the diagnostic
     * attachment and capture size. The render contract remains generic and the backends never
     * need to know that the result is being shown by a showcase panel.
     */
    suspend fun readbackAttachment(
        width: Int,
        height: Int,
        attachment: FramebufferAttachment,
    ): FramebufferAttachmentData {
        val family = world.family<Camera>()
        val cameras = family.components()
        var primary: Camera? = null
        var index = 0
        while (index < family.size) {
            if (cameras[index].isPrimary) {
                primary = cameras[index]
                break
            }
            index += 1
        }
        val camera = primary ?: return FramebufferAttachmentData.unavailable(
            attachment,
            "The showcase has no primary camera to capture.",
        )
        val target = renderer.createRenderTarget(width, height)
        return try {
            renderer.renderToTexture(target, planCapture(camera, width, height))
            renderer.readFramebufferAttachment(target, attachment)
        } finally {
            target.destroy()
        }
    }

    /** Extracted by the scene's own [RenderSystem3D], so a capture draws what the frame draws. */
    private fun planCapture(camera: Camera, width: Int, height: Int): GpuPassInput =
        (session.schedule.renderSystem ?: captureRenderSystem)
            .planCapture(world, camera, width, height)

    /** Plans captures for a scene whose infrastructure has no [RenderSystem3D] of its own. */
    private val captureRenderSystem by lazy { RenderSystem3D(renderer, gpuDrawPreparer, features = listOf(spriteFeature, tilemapFeature)) }

    /**
     * Retrieves an optional registered application service of the specified [type].
     *
     * @param T The service type.
     * @param type The [kotlin.reflect.KClass] reflection handle of the service.
     * @return The service instance, or `null` if not registered.
     */
    fun <T : Any> service(type: kotlin.reflect.KClass<T>): T? = services.service(type)

    /**
     * Retrieves a mandatory registered application service, throwing an exception if not found.
     *
     * @param T The service type.
     * @param type The [kotlin.reflect.KClass] reflection handle of the service.
     * @return The registered service instance.
     */
    fun <T : Any> requireService(type: kotlin.reflect.KClass<T>): T = services.requireService(type)

    /**
     * Resolves a registered system by its typed [SceneSystemHandle].
     *
     * @param T The system type.
     * @param handle The typed handle of the system.
     * @return The active [System] instance.
     */
    fun <T : System> system(handle: SceneSystemHandle<T>): T =
        session.system(handle)

    /**
     * Resolves a registered system by its descriptive [name].
     *
     * @param name The name identifier of the system.
     * @return The active [System] instance.
     */
    fun system(name: String): System = session.system(name)

    /**
     * Advances a specific system by invoking its update method with [delta] seconds.
     *
     * @param T The system type.
     * @param handle The typed handle of the system to update.
     * @param delta The frame delta time in seconds.
     */
    fun <T : System> update(handle: SceneSystemHandle<T>, delta: Float) {
        val system = system(handle)
        system.update(world, delta)
    }

    /**
     * Searches for an entity whose [Name] component matches [name].
     *
     * @param name The entity name to look up.
     * @return The matching [Entity] handle, or `null` if none found.
     */
    fun findEntity(name: String): Entity? {
        var result: Entity? = null
        world.queryEach(Name::class) { entity, n ->
            if (n.value == name) result = entity
        }
        return result
    }

    /** Returns a named scene entity, creating and naming it when the scene did not declare one. */
    fun findOrCreateEntity(name: String): Entity =
        findEntity(name) ?: world.create().also { world.add(it, Name(name)) }

    /**
     * Looks up an entity by [name], throwing an [IllegalStateException] if not found.
     *
     * @param name The entity name to resolve.
     * @return The resolved [Entity] handle.
     */
    fun requireEntity(name: String): Entity =
        findEntity(name) ?: error("Entity with name '$name' not found")

    /**
     * Finds the [Transform] component of an entity with the specified [name].
     *
     * @param name The entity name to look up.
     * @return The attached [Transform] component, or `null` if the entity or component is missing.
     */
    fun findTransform(name: String): Transform? =
        findEntity(name)?.let { world.get(it, Transform::class) }

    /**
     * Retrieves the [Transform] component of an entity with the specified [name], throwing if missing.
     *
     * @param name The entity name to look up.
     * @return The attached [Transform] component.
     */
    fun requireTransform(name: String): Transform = world.get(requireEntity(name), Transform::class)
        ?: error("Entity '$name' has no Transform component")

    /**
     * Finds the [Camera] component of an entity with the specified [name].
     *
     * @param name The entity name to look up.
     * @return The attached [Camera] component, or `null` if the entity or component is missing.
     */
    fun findCamera(name: String): Camera? = findEntity(name)?.let { world.get(it, Camera::class) }

    /**
     * Retrieves the [Camera] component of an entity with the specified [name], throwing if missing.
     *
     * @param name The entity name to look up.
     * @return The attached [Camera] component.
     */
    fun requireCamera(name: String): Camera = world.get(requireEntity(name), Camera::class)
        ?: error("Entity '$name' has no Camera component")

    private companion object {
        const val FRAME_TIME_HISTORY_SIZE = 30
        const val FRAME_SPREAD_HISTORY_SIZE = 240
        const val NANOS_PER_MS = 1_000_000f
    }
}

/** The standard infrastructure trio every 3D scene needs -- transform resolution, the draw
 * pass, then debug wireframes (frustum/[com.awakekt.awake.scene.rendering
 * .components.MeshBounds] boxes) drawn over whatever [RenderSystem3D] just drew. See
 * [SceneAppSpec.infrastructureSystemsFactory]'s doc comment for why this lives here instead
 * of being wired through the authoring DSL. [DebugVisualizationSystem] is a no-op (draws
 * nothing extra) unless a scene adds a
 * [com.awakekt.awake.scene.rendering.debug.WorldDebugSettings] entity and
 * toggles it on -- every existing scene is unaffected by its presence here. */
fun SceneAppLifecycleRuntime.defaultInfrastructureSystems(
    viewportProvider: () -> RenderViewport? = { null },
    renderWorldProvider: (World) -> World = { it },
    isRealtimeProvider: () -> Boolean = { true },
    isDirtyProvider: () -> Boolean = { false },
): List<System> =
    listOf(
        SpriteClipSystem(isRealtimeProvider),
        TransformSystem(),
        RenderSystem3D(
            renderer,
            gpuDrawPreparer,
            features = listOf(spriteFeature, tilemapFeature),
            viewportProvider = viewportProvider,
            renderWorldProvider = renderWorldProvider,
            isRealtimeProvider = isRealtimeProvider,
            isDirtyProvider = isDirtyProvider,
        ),
        DebugVisualizationSystem(renderer, viewportProvider),
    )

/** Stands in for `spec.ui` when only the scene's canvas elements need a UI frame. */
private val SceneCanvasOnly: SceneContent = {}
