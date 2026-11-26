/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.player.shell

import com.awakekt.awake.player.AwakePlayerConfig
import com.awakekt.awake.player.LoadedPlayerGame
import com.awakekt.awake.player.bundledProjectAssets
import com.awakekt.awake.player.createPlayerApp
import com.awakekt.awake.player.directoryAssetSource
import com.awakekt.awake.player.loadPlayerProject
import com.awakekt.awake.player.loadPlayerScene
import com.awakekt.awake.player.pointerCursorImageOf
import com.awakekt.awake.player.pointerCursorOf
import com.awakekt.awake.player.resolvePlayerTitle
import com.awakekt.awake.player.smokeTestPlayer
import com.awakekt.awake.vulkan.application.runVulkanDesktopGame
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Standalone desktop Vulkan player shell for Awake Core.
 *
 * Loads either:
 * - a packed project directory (`-Dawake.project.dir=...`, used by exported macOS `.app` bundles),
 * - a standalone declarative scene (`--scene path/to/scene.scene.json`, with no manifest required), or
 * - bundled classpath resources under `project/`.
 */
fun main(args: Array<String>) {
    val smokeTest = "--smoke-test" in args || System.getProperty("awake.smokeTest") == "true"
    val sceneArg = argumentValue(args, "--scene")
    val projectDirProp = System.getProperty("awake.project.dir")?.takeIf(String::isNotBlank)
    val titleOverride = System.getProperty("awake.app.name")?.takeIf(String::isNotBlank)

    val config = AwakePlayerConfig(
        windowTitle = titleOverride,
        hasRenderer = !smokeTest,
    )
    val game = runBlocking { loadGame(sceneArg, projectDirProp, config) }
    val title = resolvePlayerTitle(titleOverride, game.title)
    if (smokeTest) {
        try {
            println(smokeTestPlayer(game).formatLine(title))
        } finally {
            game.close()
        }
        return
    }
    val app = createPlayerApp(game, config.copy(windowTitle = title))
    runVulkanDesktopGame(
        app,
        config.renderPlan,
        cursor = pointerCursorOf(app),
        cursorImage = pointerCursorImageOf(app),
    )
}

private suspend fun loadGame(
    sceneArg: String?,
    projectDirProp: String?,
    config: AwakePlayerConfig,
): LoadedPlayerGame {
    if (sceneArg != null) {
        val sceneFile = File(sceneArg).canonicalFile
        val rootDir = projectDirProp?.let(::File)?.takeIf(File::isDirectory) ?: sceneFile.parentFile
        val relativeScene = if (rootDir != null && sceneFile.isFile) {
            sceneFile.relativeTo(rootDir.canonicalFile).invariantSeparatorsPath
        } else {
            sceneArg.replace('\\', '/')
        }
        val assets = if (rootDir != null && rootDir.isDirectory) {
            directoryAssetSource(rootDir)
        } else {
            bundledProjectAssets()
        }
        return loadPlayerScene(scenePath = relativeScene, assets = assets, config = config)
    }
    val dir = projectDirProp?.let(::File)?.takeIf(File::isDirectory)
    val source = if (dir != null) directoryAssetSource(dir) else bundledProjectAssets()
    return loadPlayerProject(source = source, config = config)
}

private fun argumentValue(args: Array<String>, flag: String): String? {
    val index = args.indexOf(flag)
    return if (index >= 0 && index + 1 < args.size) args[index + 1].takeIf(String::isNotBlank) else null
}
