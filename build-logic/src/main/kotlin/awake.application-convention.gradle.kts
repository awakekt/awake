/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import org.gradle.api.tasks.JavaExec

/**
 * Dedicated convention plugin for Awake applications and games.
 *
 * Automatically wires:
 * - macOS JVM main thread requirement (`-XstartOnFirstThread`) for GLFW/Vulkan event handling.
 * - Vulkan desktop environment (`VK_ICD_FILENAMES`, `DYLD_FALLBACK_LIBRARY_PATH`) via [VulkanDesktopEnv].
 * - Embedded native libraries (Vulkan & Naga) auto-extract via their respective native loaders.
 */
val desktopVulkanEnv = VulkanDesktopEnv.environment()

tasks.withType<JavaExec>().configureEach {
    if (HostOs.isMac) {
        jvmArgs("-XstartOnFirstThread")
    }

    if (desktopVulkanEnv.isNotEmpty()) {
        environment(desktopVulkanEnv)
    }
}
