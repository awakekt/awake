/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.app

import io.github.awakelab.awake.engine.platform.dsl.requireService
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.vulkan.application.runVulkanDesktopGame

fun main() {
    val game = uiShowcase()
    // Without this, every hover-driven cursor request (resize handles, text fields) is recorded
    // by ui-core and then dropped on the floor: runVulkanDesktopGame's `cursor` defaults to
    // null, which skips the platform call entirely. See SceneAppLifecycleRuntime.cursor.
    val runtime = game.requireService<SceneAppLifecycleRuntime>()
    runVulkanDesktopGame(
        game = game,
        applicationFactory = ::createUiShowcaseVulkanApplication,
        cursor = { runtime.cursor },
    )
}
