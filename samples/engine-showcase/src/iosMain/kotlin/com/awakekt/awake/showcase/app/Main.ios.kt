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
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.showcase.showcaseIdOf
import com.awakekt.awake.showcase.showcaseLaunchOptions
import com.awakekt.awake.vulkan.application.makeVulkanGameViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSProcessInfo
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

    return makeVulkanGameViewController(createEngineShowcaseVulkanApplication(launchedGame()))
}

/**
 * The showcase the Xcode scheme's environment asks for, as the desktop's `-P` properties do:
 * `AWAKE_SHOWCASE=many-followers` and `AWAKE_SHOWCASE_BENCHMARK=true`, and likewise `_VSYNC`,
 * `_PERF_LOG`, `_ENTITIES` and `_FOLLOWERS`. None set opens the default showcase.
 */
private fun launchedGame(): AwakeAppLifecycle {
    val environment = NSProcessInfo.processInfo.environment
    val arguments = LAUNCH_VARIABLES.mapNotNull { (variable, key) -> (environment[variable] as? String)?.let { key to it } }.toMap()
    val showcase = showcaseIdOf(arguments)
    val options = showcaseLaunchOptions(arguments)
    return if (showcase == null) engineShowcaseApp(options = options) else engineShowcaseApp(showcase, options)
}

private val LAUNCH_VARIABLES = listOf(
    "AWAKE_SHOWCASE" to "showcase",
    "AWAKE_SHOWCASE_VSYNC" to "vsync",
    "AWAKE_SHOWCASE_PERF_LOG" to "perfLog",
    "AWAKE_SHOWCASE_ENTITIES" to "entities",
    "AWAKE_SHOWCASE_FOLLOWERS" to "followers",
    "AWAKE_SHOWCASE_BENCHMARK" to "benchmark",
)
