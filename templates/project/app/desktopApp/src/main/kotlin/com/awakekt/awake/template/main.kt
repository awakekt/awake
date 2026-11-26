package com.awakekt.awake.template

import com.awakekt.awake.player.pointerCursorImageOf
import com.awakekt.awake.player.pointerCursorOf
import com.awakekt.awake.project.runtime.ProjectRenderPlan
import com.awakekt.awake.vulkan.application.runVulkanDesktopGame
import kotlinx.coroutines.runBlocking

fun main() {
    val game = createGame(runBlocking { loadGame() })
    runVulkanDesktopGame(
        game,
        ProjectRenderPlan,
        cursor = pointerCursorOf(game),
        cursorImage = pointerCursorImageOf(game),
    )
}
