package com.awakekt.awake.template

import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.webgpu.application.WebGpuEngine
import com.awakekt.awake.webgpu.application.launchWebGpuGame

fun main() {
    val game = createGame(AppWindowBackend.WEBGPU)
    // The canvas shows the pointer shape, or the custom cursor image, the UI asks for only when it
    // is handed the request.
    launchWebGpuGame(cursor = pointerCursorOf(game), cursorImage = pointerCursorImageOf(game)) {
        WebGpuEngine(appLifecycle = game, requestedPlan = AwakeRenderPlan)
    }
}
