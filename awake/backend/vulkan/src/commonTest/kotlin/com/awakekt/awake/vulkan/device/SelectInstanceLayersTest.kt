/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A machine with only the validation layer installed cannot tell the old behavior (enable every
 * installed layer) from the new one (enable validation), so the real-Vulkan headless tests pass
 * either way. These cases supply the layer list directly.
 */
class SelectInstanceLayersTest {

    @Test
    fun keepsValidationWhenInstalled() {
        assertEquals(listOf(VALIDATION_LAYER), selectInstanceLayers(listOf(VALIDATION_LAYER)))
    }

    @Test
    fun dropsEveryOtherInstalledLayer() {
        val installed = listOf(
            "VK_LAYER_LUNARG_api_dump",
            VALIDATION_LAYER,
            "VK_LAYER_LUNARG_screenshot",
            "VK_LAYER_NV_optimus",
            "VK_LAYER_RENDERDOC_Capture",
        )
        assertEquals(
            listOf(VALIDATION_LAYER),
            selectInstanceLayers(installed),
            "an API dump or frame-capture layer that happens to be installed must not be " +
                "injected into every run",
        )
    }

    @Test
    fun asksForNothingWhenValidationIsAbsent() {
        val installed = listOf("VK_LAYER_LUNARG_api_dump", "VK_LAYER_NV_optimus")
        assertEquals(emptyList(), selectInstanceLayers(installed))
    }
}
