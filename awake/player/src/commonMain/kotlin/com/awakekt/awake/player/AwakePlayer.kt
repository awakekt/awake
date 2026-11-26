/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.player

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.input.CursorImage
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.suspendRunCatching
import com.awakekt.awake.core.localization.Strings
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.select
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.error.AppCoroutineScope
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.project.runtime.LoadedProject
import com.awakekt.awake.project.runtime.ProjectRenderPlan
import com.awakekt.awake.project.runtime.SceneFeature
import com.awakekt.awake.project.runtime.loadProject
import com.awakekt.awake.project.runtime.runProject
import com.awakekt.awake.project.runtime.sceneSystems
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneContent
import kotlin.reflect.KClass

/** Default window title when neither the caller nor the project specifies one. */
const val DEFAULT_PLAYER_TITLE: String = "Awake Player"

/** Default resource directory prefix read by [bundledProjectAssets]. */
const val DEFAULT_PROJECT_PREFIX: String = "project"

private const val DEFAULT_WINDOW_WIDTH = 1280
private const val DEFAULT_WINDOW_HEIGHT = 720
private const val DEFAULT_SMOKE_FRAMES = 10
private const val SMOKE_STEP_SECONDS = 1f / 60f

/**
 * Configuration for loading and running a game with [loadPlayerProject], [loadPlayerScene], and
 * [createPlayerApp].
 *
 * @property windowTitle Explicit window title; `null` uses the loaded project or scene name.
 * @property width Initial window width in logical pixels.
 * @property height Initial window height in logical pixels.
 * @property windowBackend Rendering backend requested for the window.
 * @property touchControls Whether on-screen touch controls should be enabled.
 * @property features Additional game-specific [SceneFeature]s loaded alongside Core features.
 * @property renderPlan Render plan handed to the host GPU backend (defaults to [ProjectRenderPlan]).
 * @property hasRenderer Set `false` for headless loading where GPU textures and render meshes are skipped.
 */
data class AwakePlayerConfig(
    val windowTitle: String? = null,
    val width: Int = DEFAULT_WINDOW_WIDTH,
    val height: Int = DEFAULT_WINDOW_HEIGHT,
    val windowBackend: AppWindowBackend = AppWindowBackend.VULKAN,
    val touchControls: Boolean = false,
    val features: List<SceneFeature> = emptyList(),
    val renderPlan: RenderPlan = ProjectRenderPlan,
    val hasRenderer: Boolean = true,
)

/**
 * A loaded Awake game ready for [createPlayerApp] or [smokeTestPlayer].
 *
 * Owns the underlying [LoadedProject] (and any native physics world it created); call [close] once
 * playback ends.
 */
class LoadedPlayerGame internal constructor(
    val project: LoadedProject,
    val title: String,
) : AutoCloseable {
    /** Project localization tables when the project ships `strings/en.json`, or `null` otherwise. */
    val strings: Strings? get() = project.strings

    override fun close() {
        project.close()
    }
}

/**
 * Summary produced by [smokeTestPlayer] after instantiating and ticking a [LoadedPlayerGame]
 * headlessly.
 */
data class PlayerSmokeReport(
    val projectId: String,
    val projectName: String,
    val entryScene: String,
    val nodeCount: Int,
    val frames: Int,
) {
    /** Canonical single-line marker parsed by CI smoke checks for prebuilt players. */
    fun formatLine(title: String = projectName): String =
        "AWAKE_PLAYER_OK title=$title project=$projectId name=$projectName scene=$entryScene nodes=$nodeCount frames=$frames"
}

/**
 * Reads bundled game files packaged under classpath/web resources at [prefix] (defaults to
 * `"project"`).
 */
fun bundledProjectAssets(prefix: String = DEFAULT_PROJECT_PREFIX): AssetSource {
    val cleanPrefix = prefix.trim().trim('/')
    require(cleanPrefix.isNotEmpty() && !cleanPrefix.contains("..")) {
        "Invalid bundled project prefix: '$prefix'"
    }
    return AssetSource { path ->
        suspendRunCatching { readResourceBytes("$cleanPrefix/${path.value}") }
    }
}

/**
 * An [AssetSource] that contains no files; useful with [loadPlayerSceneDocument] when a standalone
 * declarative scene needs no external `.glb`, `.wav`, or texture files.
 */
val EmptyAssetSource: AssetSource = AssetSource { path ->
    Result.failure(IllegalArgumentException("Asset not found: ${path.value}"))
}

/**
 * Loads a complete Awake project (`awake.project.json`, entry scene, models, shaders, physics, and
 * localization tables) from [source].
 */
suspend fun loadPlayerProject(
    source: AssetSource = bundledProjectAssets(),
    config: AwakePlayerConfig = AwakePlayerConfig(),
    physicsWorld: (suspend () -> PhysicsWorld)? = ::createJoltPhysicsWorld,
    strings: Strings? = null,
    onProgress: (Float) -> Unit = {},
): LoadedPlayerGame {
    val project = loadProject(
        files = source,
        capabilities = config.features,
        hasRenderer = config.hasRenderer,
        onProgress = onProgress,
        strings = strings,
        physicsWorld = physicsWorld,
    )
    val resolvedTitle = resolvePlayerTitle(config.windowTitle, project.manifest.name)
    return LoadedPlayerGame(project = project, title = resolvedTitle)
}

/**
 * Loads a single declarative `.scene.json` at [scenePath] from [assets] without requiring an
 * `awake.project.json` manifest or Awake Studio.
 *
 * A minimal valid project manifest is synthesized in memory pointing at [scenePath], while all
 * referenced models, textures, shaders, audio, and prefabs resolve from [assets].
 */
suspend fun loadPlayerScene(
    scenePath: String,
    assets: AssetSource = bundledProjectAssets(),
    config: AwakePlayerConfig = AwakePlayerConfig(),
    physicsWorld: (suspend () -> PhysicsWorld)? = ::createJoltPhysicsWorld,
    strings: Strings? = null,
    onProgress: (Float) -> Unit = {},
): LoadedPlayerGame {
    val normalized = AssetPath(scenePath)
    val fallbackName = deriveSceneTitle(normalized.value)
    val virtualSource = virtualSceneProjectSource(
        entryScenePath = normalized.value,
        projectName = config.windowTitle?.takeIf(String::isNotBlank) ?: fallbackName,
        backing = assets,
    )
    return loadPlayerProject(
        source = virtualSource,
        config = config,
        physicsWorld = physicsWorld,
        strings = strings,
        onProgress = onProgress,
    )
}

/**
 * Loads a standalone declarative scene directly from its raw JSON [sceneJson] string, resolving
 * any referenced external assets from [assets].
 */
suspend fun loadPlayerSceneDocument(
    sceneJson: String,
    name: String = DEFAULT_PLAYER_TITLE,
    assets: AssetSource = EmptyAssetSource,
    config: AwakePlayerConfig = AwakePlayerConfig(),
    physicsWorld: (suspend () -> PhysicsWorld)? = ::createJoltPhysicsWorld,
    strings: Strings? = null,
    onProgress: (Float) -> Unit = {},
): LoadedPlayerGame {
    val resolvedName = config.windowTitle?.takeIf(String::isNotBlank) ?: name.takeIf(String::isNotBlank) ?: DEFAULT_PLAYER_TITLE
    val virtualSource = virtualSceneDocumentSource(
        sceneJson = sceneJson,
        projectName = resolvedName,
        backing = assets,
    )
    return loadPlayerProject(
        source = virtualSource,
        config = config,
        physicsWorld = physicsWorld,
        strings = strings,
        onProgress = onProgress,
    )
}

/**
 * Builds an [AwakeAppLifecycle] that plays [game] with [config] and an optional Compose [overlay].
 */
fun createPlayerApp(
    game: LoadedPlayerGame,
    config: AwakePlayerConfig = AwakePlayerConfig(),
    overlay: SceneContent? = null,
): AwakeAppLifecycle = app {
    window {
        title = resolvePlayerTitle(config.windowTitle, game.title)
        size(config.width, config.height)
        backend.select(config.windowBackend)
    }
    scene("game") {
        runProject(game.project, touchControls = config.touchControls)
        if (overlay != null) {
            ui(overlay)
        }
    }
}

/**
 * Resolves the window title: an explicit [overrideTitle], then [projectTitle], else
 * [DEFAULT_PLAYER_TITLE].
 */
fun resolvePlayerTitle(overrideTitle: String?, projectTitle: String? = null): String =
    overrideTitle?.takeIf(String::isNotBlank)
        ?: projectTitle?.takeIf(String::isNotBlank)
        ?: DEFAULT_PLAYER_TITLE

/**
 * Pointer cursor shape requested each frame by the running player's UI, to forward into
 * `runVulkanDesktopGame` or `launchWebGpuGame`.
 */
fun pointerCursorOf(app: AwakeAppLifecycle): () -> PointerCursor {
    val runtime = app.requireService<SceneAppLifecycleRuntime>()
    return { runtime.cursor }
}

/**
 * Custom pointer cursor image requested each frame by the running player's UI, or `null` when
 * using the system cursor shape.
 */
fun pointerCursorImageOf(app: AwakeAppLifecycle): () -> CursorImage? {
    val runtime = app.requireService<SceneAppLifecycleRuntime>()
    return { runtime.effectiveCursorImage }
}

/**
 * Instantiates and ticks [game] headlessly for [frames] steps to verify a project or prebuilt
 * player export without opening a window.
 */
fun smokeTestPlayer(
    game: LoadedPlayerGame,
    frames: Int = DEFAULT_SMOKE_FRAMES,
): PlayerSmokeReport {
    require(frames >= 0) { "frames must be >= 0, got $frames" }
    val world = World()
    val project = game.project
    project.scene.instantiate(world = world)
    val idle = GameplayInput(Input().currentSnapshot, InputOwnership())
    AppCoroutineScope(SmokeServices).use { scope ->
        project.sceneSystems({ idle }, coroutineScope = scope).use { systems ->
            repeat(frames) {
                systems.fixed.forEach { it.update(world, SMOKE_STEP_SECONDS) }
                systems.frame.forEach { it.update(world, SMOKE_STEP_SECONDS) }
            }
        }
    }
    return PlayerSmokeReport(
        projectId = project.manifest.id,
        projectName = project.manifest.name,
        entryScene = project.manifest.entryScene,
        nodeCount = project.scene.nodes.size,
        frames = frames,
    )
}

private val SmokeServices = object : AppServiceLookup {
    override fun <T : Any> service(type: KClass<T>): T? = null
}
