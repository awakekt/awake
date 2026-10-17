package com.awakekt.awake.template

import com.awakekt.awake.vulkan.application.runVulkanDesktopGame
import kotlinx.coroutines.runBlocking

fun main() {
    val game = createGame(runBlocking { loadGame() })
    // The window shows the pointer shape the UI asks for only when it is handed the request.
    runVulkanDesktopGame(game, AwakeRenderPlan, cursor = pointerCursorOf(game))
}
