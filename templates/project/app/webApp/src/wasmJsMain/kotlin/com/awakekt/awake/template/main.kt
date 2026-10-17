package com.awakekt.awake.template

import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.webgpu.application.WebGpuEngine
import com.awakekt.awake.webgpu.application.launchWebGpuGame
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

fun main() {
    MainScope().launch {
        val game = createGame(loadGame(), AppWindowBackend.WEBGPU)
        // The canvas shows the pointer shape the UI asks for only when it is handed the request.
        launchWebGpuGame(cursor = pointerCursorOf(game)) {
            WebGpuEngine(appLifecycle = game, requestedPlan = AwakeRenderPlan)
        }
    }
}
