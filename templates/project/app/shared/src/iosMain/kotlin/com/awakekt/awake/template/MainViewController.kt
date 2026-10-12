package com.awakekt.awake.template

import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.vulkan.application.makeVulkanGameViewController
import kotlinx.coroutines.runBlocking

// The project is read before the first frame; the app bundle must hold the `project` folder.
fun MainViewController() = makeVulkanGameViewController(
    VulkanEngine(
        appLifecycle = createGame(runBlocking { loadGame() }, touchControls = true),
        plan = AwakeRenderPlan,
    ),
)
