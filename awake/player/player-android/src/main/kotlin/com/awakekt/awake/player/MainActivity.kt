/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.player

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.error.AppCoroutineScope
import com.awakekt.awake.engine.window.AwakeSurfaceView
import com.awakekt.awake.vulkan.application.VulkanEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private val NoAppServices = object : AppServiceLookup {
    override fun <T : Any> service(type: KClass<T>): T? = null
}

/**
 * Standalone Android Vulkan player activity for Awake Core.
 *
 * Plays either a packed project under `assets/project/` (exported by Awake Studio or packaged in
 * a standalone APK), a single declarative scene passed via intent string extra `"scene"`, or
 * classpath resources under `project/`.
 */
class MainActivity : Activity() {
    private var loadScope: AppCoroutineScope? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sceneExtra = intent?.getStringExtra("scene")?.takeIf(String::isNotBlank)
        val fallback = bundledProjectAssets()
        val source = AssetSource { path ->
            runCatching {
                assets.open("project/${path.value}").use { it.readBytes() }
            }.recoverCatching {
                fallback.read(path).getOrThrow()
            }
        }

        val scope = AppCoroutineScope(NoAppServices, Dispatchers.IO).also { loadScope = it }
        scope.launch {
            val config = AwakePlayerConfig(touchControls = true)
            val game = if (sceneExtra != null) {
                loadPlayerScene(scenePath = sceneExtra, assets = source, config = config)
            } else {
                loadPlayerProject(source = source, config = config)
            }
            val title = resolvePlayerTitle(null, game.title)
            Log.i("AwakePlayer", "AWAKE_ANDROID_PLAYER_OK name=$title")
            val engine = VulkanEngine(createPlayerApp(game, config.copy(windowTitle = title)), config.renderPlan)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    setContentView(AwakeSurfaceView(this@MainActivity, engine))
                } else {
                    game.close()
                }
            }
        }
    }

    override fun onDestroy() {
        loadScope?.close()
        loadScope = null
        super.onDestroy()
    }
}
