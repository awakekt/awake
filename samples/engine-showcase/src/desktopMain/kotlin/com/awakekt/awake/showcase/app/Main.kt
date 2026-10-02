/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.app

import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.PrintLogSink
import com.awakekt.awake.showcase.DEFAULT_SHOWCASE_ID
import com.awakekt.awake.showcase.ShowcaseLaunchOptions
import com.awakekt.awake.vulkan.application.runVulkanDesktopGame
import java.awt.Taskbar
import javax.imageio.ImageIO

fun main() {
    // Warnings and errors reach the terminal, such as a shader replacement being refused.
    Log.install(PrintLogSink(minimumLevel = LogLevel.Warn))
    configureDesktopBranding()
    val showcaseId = System.getProperty(SHOWCASE_PROPERTY)?.ifBlank { DEFAULT_SHOWCASE_ID } ?: DEFAULT_SHOWCASE_ID
    val defaults = ShowcaseLaunchOptions()
    val options = ShowcaseLaunchOptions(
        vsync = System.getProperty(VSYNC_PROPERTY)?.toBooleanStrictOrNull() ?: defaults.vsync,
        perfLog = System.getProperty(PERF_LOG_PROPERTY)?.toBooleanStrictOrNull() ?: defaults.perfLog,
        stressEntities = System.getProperty(ENTITIES_PROPERTY)?.toIntOrNull()?.takeIf { it > 0 } ?: defaults.stressEntities,
    )
    val game = engineShowcaseApp(showcaseId, options)
    runVulkanDesktopGame(game, ::createEngineShowcaseVulkanApplication)
}

private fun configureDesktopBranding() {
    if (!Taskbar.isTaskbarSupported()) return
    val taskbar = Taskbar.getTaskbar()
    if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return
    val icon = checkNotNull(
        Thread.currentThread().contextClassLoader.getResourceAsStream(DESKTOP_ICON_RESOURCE),
    ) { "Missing Desktop brand asset: $DESKTOP_ICON_RESOURCE" }.use(ImageIO::read)
    taskbar.iconImage = checkNotNull(icon) { "Desktop brand asset is not a decodable image." }
}

// Each is forwarded from the same-named -P Gradle property; see build.gradle.kts.
private const val SHOWCASE_PROPERTY = "awake.showcase"
private const val VSYNC_PROPERTY = "awake.showcase.vsync"
private const val PERF_LOG_PROPERTY = "awake.showcase.perfLog"
private const val ENTITIES_PROPERTY = "awake.showcase.entities"
private const val DESKTOP_ICON_RESOURCE = "brand/awake-mark.png"
