package com.awakekt.awake.template

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.localization.StringTableSource
import com.awakekt.awake.core.localization.Strings
import com.awakekt.awake.core.localization.systemLocale
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.player.AwakePlayerConfig
import com.awakekt.awake.player.LoadedPlayerGame
import com.awakekt.awake.player.bundledProjectAssets
import com.awakekt.awake.player.createPlayerApp
import com.awakekt.awake.player.loadPlayerProject

/** The window title. */
const val GAME_TITLE = "Awake Project"

/**
 * The Awake project this app plays: everything under `project/` in the shared module's resources.
 * Replace that folder with a project from Awake Studio to play it instead of the sample.
 */
val BundledProject: AssetSource = bundledProjectAssets()

/** What the app needs before its first frame: the project and the text its overlay shows. */
class GameData(val game: LoadedPlayerGame, val strings: Strings) : AutoCloseable by game

/**
 * Reads the bundled project and its entry scene, and its string tables (`project/strings/<tag>.json`)
 * in the device's language, falling back to English when the project has no table for it.
 */
suspend fun loadGame(): GameData {
    val strings = Strings.load(StringTableSource.assets(BundledProject), locale = systemLocale())
    val game = loadPlayerProject(
        source = BundledProject,
        config = AwakePlayerConfig(windowTitle = GAME_TITLE),
        strings = strings,
    )
    return GameData(game = game, strings = strings)
}

/** Creates a fresh application lifecycle that plays [data]; [touchControls] shows its on-screen controls. */
fun createGame(
    data: GameData,
    windowBackend: AppWindowBackend = AppWindowBackend.VULKAN,
    touchControls: Boolean = false,
): AwakeAppLifecycle = createPlayerApp(
    game = data.game,
    config = AwakePlayerConfig(
        windowTitle = GAME_TITLE,
        windowBackend = windowBackend,
        touchControls = touchControls,
    ),
    overlay = { Hud(data.strings) },
)
