/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.app

import io.github.awakelab.awake.engine.platform.dsl.requireService
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.vulkan.application.runVulkanDesktopGame

fun main() {
    val app = studioApp()
    // Without this, every hover-driven cursor request (the workspace resize handles) is
    // recorded by ui-core and then dropped: runVulkanDesktopGame's `cursor` defaults to null,
    // which skips the platform call entirely. See SceneAppLifecycleRuntime.cursor.
    val runtime = app.requireService<SceneAppLifecycleRuntime>()
    runVulkanDesktopGame(
        game = app,
        applicationFactory = ::createStudioVulkanApplication,
        cursor = { runtime.cursor },
    )
}
