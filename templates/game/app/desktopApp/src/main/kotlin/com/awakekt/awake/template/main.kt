package com.awakekt.awake.template

import com.awakekt.awake.vulkan.application.runVulkanDesktopGame

fun main() {
    val game = createGame()
    // The window shows the pointer shape the UI asks for only when it is handed the request. The custom
    // cursor image (`cursorImage = pointerCursorImageOf(game)`) waits for the Vulkan release that adds it:
    // the template builds against the latest Vulkan release on Maven Central.
    runVulkanDesktopGame(game, AwakeRenderPlan, cursor = pointerCursorOf(game))
}
