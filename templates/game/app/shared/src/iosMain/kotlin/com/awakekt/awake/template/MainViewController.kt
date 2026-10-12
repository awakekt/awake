package com.awakekt.awake.template

import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.vulkan.application.makeVulkanGameViewController

fun MainViewController() = makeVulkanGameViewController(
    VulkanEngine(
        appLifecycle = createGame(),
        plan = AwakeRenderPlan,
    ),
)
