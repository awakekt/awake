/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.composeshowcase.app

import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.vulkan.application.runVulkanDesktopGame

fun main() {
    val game = composeShowcase()
    val runtime = game.requireService<SceneAppLifecycleRuntime>()
    runVulkanDesktopGame(
        game = game,
        applicationFactory = ::createComposeShowcaseVulkanApplication,
        cursor = { runtime.cursor },
    )
}
