package com.awakekt.awake.template

import com.awakekt.awake.vulkan.application.runVulkanDesktopGame
import kotlinx.coroutines.runBlocking

fun main() {
    val game = runBlocking { loadGame() }
    runVulkanDesktopGame(createGame(game), AwakeRenderPlan)
}
