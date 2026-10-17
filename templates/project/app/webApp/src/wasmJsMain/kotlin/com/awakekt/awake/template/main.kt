package com.awakekt.awake.template

import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.webgpu.application.WebGpuEngine
import com.awakekt.awake.webgpu.application.launchWebGpuGame
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

fun main() {
    MainScope().launch {
        val game = loadGame()
        launchWebGpuGame {
            WebGpuEngine(
                appLifecycle = createGame(game, AppWindowBackend.WEBGPU),
                requestedPlan = AwakeRenderPlan,
            )
        }
    }
}
