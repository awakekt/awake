package com.awakekt.awake.template

import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.localization.StringTableSource
import com.awakekt.awake.core.localization.Strings
import com.awakekt.awake.core.localization.systemLocale
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.select
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime

/** The window title. */
const val GAME_TITLE = "Awake Project"

/**
 * The Awake project this app plays: everything under `project/` in the shared module's resources.
 * Replace that folder with a project from Awake Studio to play it instead of the sample.
 */
val BundledProject = AssetSource { path -> runCatching { readResourceBytes("project/${path.value}") } }

/** What the app needs before its first frame: the project and the text its overlay shows. */
class GameData(val project: LoadedProject, val strings: Strings)

/**
 * Reads the bundled project and its entry scene, and its string tables (`project/strings/<tag>.json`)
 * in the device's language, falling back to English when the project has no table for it.
 */
suspend fun loadGame(): GameData = GameData(
    project = loadProject(BundledProject, physicsWorld = ::createJoltPhysicsWorld),
    strings = Strings.load(StringTableSource.assets(BundledProject), locale = systemLocale()),
)

/** Creates a fresh application lifecycle that plays [game]; [touchControls] shows its on-screen controls. */
fun createGame(
    game: GameData,
    windowBackend: AppWindowBackend = AppWindowBackend.VULKAN,
    touchControls: Boolean = false,
): AwakeAppLifecycle = app {
    window {
        title = GAME_TITLE
        size(1280, 720)
        backend.select(windowBackend)
    }
    scene("game") {
        runProject(game.project, touchControls)
        ui { Hud(game.strings) }
    }
}

/**
 * The pointer shape [game]'s UI asks for each frame, such as a text beam over a text field. The
 * desktop window and the browser canvas show it when their host is given it as `cursor`; without it
 * they keep the default arrow.
 */
fun pointerCursorOf(game: AwakeAppLifecycle): () -> PointerCursor {
    val runtime = game.requireService<SceneAppLifecycleRuntime>()
    return { runtime.cursor }
}
