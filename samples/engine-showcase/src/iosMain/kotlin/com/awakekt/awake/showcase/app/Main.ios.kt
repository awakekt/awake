/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.awakekt.awake.showcase.app

import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.NSLogSink
import com.awakekt.awake.vulkan.application.makeVulkanGameViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

private var loggerInitialized = false

/** Constructs a live UIViewController hosting the Engine Showcase Vulkan 3D scene on iOS. */
@Suppress("TooGenericExceptionCaught")
fun makeEngineShowcaseViewController(): UIViewController {
    // Initialize iOS logging on first call - must happen before any engine initialization
    if (!loggerInitialized) {
        try {
            Log.minimumLevel = LogLevel.Debug
            Log.install(NSLogSink(minimumLevel = LogLevel.Debug))
            loggerInitialized = true
        } catch (t: Throwable) {
            // If logging setup fails, we still need to continue - NSLog as fallback
            platform.Foundation.NSLog("FATAL: Failed to initialize iOS logging: ${t.message}")
        }
    }

    return makeVulkanGameViewController(createEngineShowcaseVulkanApplication())
}
