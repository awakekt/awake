/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.player.shell

import com.awakekt.awake.core.audio.AudioPlayerFactory
import com.awakekt.awake.core.audio.WebAudioPlayer
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.engine.platform.error.AppCoroutineScope
import com.awakekt.awake.player.AwakePlayerConfig
import com.awakekt.awake.player.bundledProjectAssets
import com.awakekt.awake.player.createPlayerApp
import com.awakekt.awake.player.loadPlayerProject
import com.awakekt.awake.player.loadPlayerScene
import com.awakekt.awake.player.pointerCursorImageOf
import com.awakekt.awake.player.pointerCursorOf
import com.awakekt.awake.webgpu.application.WebGpuEngine
import com.awakekt.awake.webgpu.application.launchWebGpuGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private val EmptyAppServices = object : AppServiceLookup {
    override fun <T : Any> service(type: KClass<T>): T? = null
}

/**
 * Standalone browser WebGPU player shell for Awake Core.
 *
 * Serves either a bundled `project/` directory next to `index.html`, or a standalone declarative
 * scene when `?scene=<relative-path>` is present in the page query string.
 */
fun main() {
    val audioPlayer = WebAudioPlayer()
    AudioPlayerFactory.provider = { audioPlayer }

    val bootstrapScope = AppCoroutineScope(EmptyAppServices, Dispatchers.Main)
    bootstrapScope.launch {
        val sceneParam = queryParam(locationSearch(), "scene")
        val config = AwakePlayerConfig(windowBackend = AppWindowBackend.WEBGPU)
        val assets = bundledProjectAssets()
        val game = if (sceneParam != null) {
            loadPlayerScene(scenePath = sceneParam, assets = assets, config = config)
        } else {
            loadPlayerProject(source = assets, config = config)
        }
        val app = createPlayerApp(game, config)
        launchWebGpuGame(
            cursor = pointerCursorOf(app),
            cursorImage = pointerCursorImageOf(app),
        ) {
            WebGpuEngine(appLifecycle = app, requestedPlan = config.renderPlan)
        }
    }
}

internal fun queryParam(search: String, name: String): String? =
    search.removePrefix("?").split('&').firstNotNullOfOrNull { pair ->
        val key = pair.substringBefore('=', "")
        if (key == name) pair.substringAfter('=', "").takeIf(String::isNotBlank) else null
    }

private fun locationSearch(): String = js("window.location.search")
