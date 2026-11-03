package com.awakekt.awake.template

import com.awakekt.awake.vulkan.application.runVulkanDesktopGame
import kotlinx.coroutines.runBlocking

fun main() {
    val game = createGame(runBlocking { loadGame() })
    runVulkanDesktopGame(
        game,
        AwakeRenderPlan,
        cursor = pointerCursorOf(game),
        cursorImage = pointerCursorImageOf(game),
    )
}
