package com.awakekt.awake.template

import com.awakekt.awake.vulkan.application.runVulkanDesktopGame

fun main() {
    val game = createGame()
    // The window shows the pointer shape, or the custom cursor image, the UI asks for only when it
    // is handed the request.
    runVulkanDesktopGame(
        game,
        AwakeRenderPlan,
        cursor = pointerCursorOf(game),
        cursorImage = pointerCursorImageOf(game),
    )
}
