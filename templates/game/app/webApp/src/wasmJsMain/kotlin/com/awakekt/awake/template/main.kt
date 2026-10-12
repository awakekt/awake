package com.awakekt.awake.template

import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.webgpu.application.WebGpuEngine
import com.awakekt.awake.webgpu.application.launchWebGpuGame

fun main() = launchWebGpuGame {
    WebGpuEngine(
        appLifecycle = createGame(AppWindowBackend.WEBGPU),
        requestedPlan = AwakeRenderPlan,
    )
}
