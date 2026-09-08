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
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.engine.platform.lifecycle.AppLifecycle
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.DrawCall
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.RenderSystem
import com.awakekt.awake.scene.rendering.debug.DebugVisualizationSystem
import com.awakekt.awake.scene.rendering.mesh.InstancedMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.InstancedSkinnedMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.session.SceneSession
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * Orchestrates a single 3D scene session.
 */
class SceneAppLifecycleRuntime internal constructor(
    val spec: SceneAppSpec,
) : AppLifecycle {
    val session = SceneSession(spec)

    val world: World
        get() = session.world
    lateinit var renderer: Renderer
        private set

    val sceneManager: SceneManager
        get() = session.sceneManager

    /** Compatibility-only host for the legacy `content {}` DSL. New applications install an
     * app-level Compose module instead. */
    val uiHost: ComposeHost by lazy(::ComposeHost)
    private val graphicsLayers = GraphicsLayerCompositor()

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

    /** Last frame's draw commands, for a test that has to assert on what was painted. */
    var uiPrimitives: List<UiDrawPrimitive> = emptyList()
        private set

    /** Rolling window backing [averageFrameTimeMs]/[fps] -- see `SceneAppFrame.kt`'s
     * `frameStats()`, which reads these. */
    private val frameTimesMs = ArrayDeque<Float>()

    private fun recordFrameTime(deltaSeconds: Float) {
        frameTimesMs.addLast(deltaSeconds * 1000f)
        while (frameTimesMs.size > FRAME_TIME_HISTORY_SIZE) frameTimesMs.removeFirst()
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
            ScenePhaseStats(uiBuildMs, uiWaitMs, uiStageMs, simRenderMs)
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
        // Install built-in scene component resolvers (Camera, Light, PbrMaterial, SpinControl,
        // MeshRenderer, PrefabLink) into the global SceneComponentRegistry. Explicit here
        // rather than in DefaultSceneComponentResolvers.init{} so tests can run without
        // triggering global state, and so subclasses can opt out or replace the set.
        DefaultSceneComponentResolvers.install()
        session.initialize()
    }

    override suspend fun ready(renderer: Renderer) {
        this.renderer = renderer
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

        val uiFrame = spec.ui?.let {
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
                            primitives = builtFrame.primitives,
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
            cursor = uiFrame.cursor
            uiOwnership = uiFrame.ownership
            uiSemantics = uiFrame.semantics
            uiPrimitives = uiFrame.primitives
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

        // 3. Simulation & Infrastructure Pump
        timePhase({ simRenderMs = it }) {
            session.advance(delta) { step ->
                spec.updateBlock(this, step, snapshot)
            }
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
            ?: return SceneFrame(emptyList(), emptyList(), emptyList(), InputOwnership(), PointerCursor.Default, false)
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
                content()
            }
        }
        return SceneFrame(
            frame.primitives,
            frame.graphicsLayers,
            frame.semantics,
            frame.ownership,
            frame.effects.cursor,
            frame.effects.requestKeyboard,
        )
    }

    override fun resize(width: Float, height: Float) = Unit

    override fun dispose() {
        graphicsLayers.dispose()
        session.dispose(this)
    }

    fun requireAssetLibrary(): SceneAssetLibrary = session.requireAssetLibrary()

    fun requireMesh(name: String): Mesh = session.requireMesh(this, name)

    fun requireMaterial(name: String): Material = session.requireMaterial(this, name)

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

    fun <T : System> system(handle: SceneSystemHandle<T>): T =
        session.system(handle)

    fun system(name: String): System = session.system(name)

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

    private companion object {
        const val FRAME_TIME_HISTORY_SIZE = 30
        const val NANOS_PER_MS = 1_000_000f
    }
}

/** The standard infrastructure trio every 3D scene needs -- transform resolution, the draw
 * pass, then debug wireframes (frustum/[com.awakekt.awake.scene.rendering
 * .components.MeshBounds] boxes) drawn over whatever [RenderSystem] just drew. See
 * [SceneAppSpec.infrastructureSystemsFactory]'s doc comment for why this lives here instead
 * of being wired through the authoring DSL. [DebugVisualizationSystem] is a no-op (draws
 * nothing extra) unless a scene adds a
 * [com.awakekt.awake.scene.rendering.debug.WorldDebugSettings] entity and
 * toggles it on -- every existing scene is unaffected by its presence here. */
fun SceneAppLifecycleRuntime.defaultInfrastructureSystems(): List<System> =
    listOf(TransformSystem(), RenderSystem(renderer), DebugVisualizationSystem(renderer))
