/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.composeshowcase.app

import io.github.awakelab.awake.engine.platform.dsl.requireService
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.vulkan.application.runVulkanDesktopGame

fun main() {
    val game = composeShowcase()
    val runtime = game.requireService<SceneAppLifecycleRuntime>()
    runVulkanDesktopGame(
        game = game,
        applicationFactory = ::createComposeShowcaseVulkanApplication,
        cursor = { runtime.cursor },
    )
}
