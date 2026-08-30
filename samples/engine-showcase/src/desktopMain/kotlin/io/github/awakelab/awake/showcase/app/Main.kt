/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase.app

import io.github.awakelab.awake.showcase.DEFAULT_SHOWCASE_ID
import io.github.awakelab.awake.vulkan.application.runVulkanDesktopGame

fun main() {
    val showcaseId = System.getProperty(SHOWCASE_PROPERTY)?.ifBlank { DEFAULT_SHOWCASE_ID } ?: DEFAULT_SHOWCASE_ID
    val game = engineShowcaseApp(showcaseId)
    runVulkanDesktopGame(game, ::createEngineShowcaseVulkanApplication)
}

private const val SHOWCASE_PROPERTY = "awake.showcase"
