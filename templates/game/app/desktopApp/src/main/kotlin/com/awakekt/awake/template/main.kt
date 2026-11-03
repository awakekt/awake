package com.awakekt.awake.template

import com.awakekt.awake.vulkan.application.runVulkanDesktopGame

fun main() {
    val game = createGame()
    runVulkanDesktopGame(
        game,
        AwakeRenderPlan,
        cursor = pointerCursorOf(game),
        cursorImage = pointerCursorImageOf(game),
    )
}
