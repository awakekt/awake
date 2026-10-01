/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class VulkanDesktopEnvTest {
    @Test
    fun aRunValidatesUnlessThePropertyTurnsItOff() {
        val project = ProjectBuilder.builder().build()
        assertEquals("1", VulkanDesktopEnv.runEnvironment(project)["AWAKE_VULKAN_VALIDATION"])

        project.extensions.extraProperties["awake.vulkan.validation"] = "false"
        assertEquals("0", VulkanDesktopEnv.runEnvironment(project)["AWAKE_VULKAN_VALIDATION"])
    }
}
