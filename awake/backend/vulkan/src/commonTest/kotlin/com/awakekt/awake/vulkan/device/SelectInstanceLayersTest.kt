/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.core.config.MapEnvSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The real-Vulkan headless tests cannot tell these cases apart on a machine with only the
 * validation layer installed, so the layer list and request are supplied directly.
 */
class SelectInstanceLayersTest {

    @Test
    fun keepsValidationWhenRequestedAndInstalled() {
        assertEquals(listOf(VALIDATION_LAYER), selectInstanceLayers(listOf(VALIDATION_LAYER), validation = true))
    }

    @Test
    fun asksForNothingUnlessRequested() {
        assertEquals(
            emptyList(),
            selectInstanceLayers(listOf(VALIDATION_LAYER), validation = false),
            "an installed Vulkan SDK must not turn validation on in a shipped app",
        )
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
            selectInstanceLayers(installed, validation = true),
            "an API dump or frame-capture layer that happens to be installed must not be " +
                "injected into every run",
        )
    }

    @Test
    fun asksForNothingWhenValidationIsAbsent() {
        val installed = listOf("VK_LAYER_LUNARG_api_dump", "VK_LAYER_NV_optimus")
        assertEquals(emptyList(), selectInstanceLayers(installed, validation = true))
    }

    @Test
    fun readsTheRequestFromTheEnvironment() {
        assertFalse(validationRequested(MapEnvSource()))
        assertFalse(validationRequested(MapEnvSource(mapOf(VALIDATION_PROPERTY to "false"))))
        assertTrue(validationRequested(MapEnvSource(mapOf(VALIDATION_PROPERTY to "true"))))
        assertTrue(validationRequested(MapEnvSource(mapOf(VALIDATION_PROPERTY to " 1 "))))
    }
}
