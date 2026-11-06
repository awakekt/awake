/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.awakekt.awake.vulkan.application

import com.awakekt.awake.engine.window.AwakeMetalView
import com.awakekt.awake.engine.window.AwakeViewController
import platform.UIKit.UIScreen
import platform.UIKit.UIViewController

/**
 * Wraps a [VulkanEngine] in a UIKit view controller, passing the view's safe area on to it. The
 * controller is an [AwakeViewController], so `iosHostActions(controller)` gives the game
 * `HostActions.fullscreen`, which hides the status bar and home indicator.
 */
fun makeVulkanGameViewController(
    application: VulkanEngine,
): UIViewController {
    val controller = AwakeViewController()
    controller.view = AwakeMetalView(
        frame = UIScreen.mainScreen.bounds,
        input = application.input,
        onCreate = { metalLayer -> application.create(metalLayer) },
        onUpdate = { delta -> application.update(delta) },
        onResize = { width, height -> application.resize(0, 0, width, height) },
        onPause = { application.pause() },
        onResume = { application.resume() },
        onSafeArea = { insets -> application.setSafeArea(insets) },
    )
    return controller
}
