/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.app

import com.awakekt.awake.showcase.DEFAULT_SHOWCASE_ID
import com.awakekt.awake.vulkan.application.runVulkanDesktopGame
import java.awt.Taskbar
import javax.imageio.ImageIO

fun main() {
    configureDesktopBranding()
    val showcaseId = System.getProperty(SHOWCASE_PROPERTY)?.ifBlank { DEFAULT_SHOWCASE_ID } ?: DEFAULT_SHOWCASE_ID
    val game = engineShowcaseApp(showcaseId)
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

private const val SHOWCASE_PROPERTY = "awake.showcase"
private const val DESKTOP_ICON_RESOURCE = "brand/awake-mark.png"
