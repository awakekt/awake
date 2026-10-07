/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.swapchain

import com.awakekt.awake.vulkan.enums.VkSurfaceTransformFlagBitsKHR.VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR
import com.awakekt.awake.vulkan.enums.VkSurfaceTransformFlagBitsKHR.VK_SURFACE_TRANSFORM_ROTATE_180_BIT_KHR
import com.awakekt.awake.vulkan.enums.VkSurfaceTransformFlagBitsKHR.VK_SURFACE_TRANSFORM_ROTATE_270_BIT_KHR
import com.awakekt.awake.vulkan.enums.VkSurfaceTransformFlagBitsKHR.VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkSurfaceCapabilitiesKHR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How a swapchain follows the device's rotation (the stretched-landscape Android bug). */
class SwapchainOrientationTest {
    private val portrait = VkExtent2D(1080, 2400)
    private val allTransforms = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR.value or
        VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR.value or
        VK_SURFACE_TRANSFORM_ROTATE_180_BIT_KHR.value or
        VK_SURFACE_TRANSFORM_ROTATE_270_BIT_KHR.value

    private fun surface(transform: com.awakekt.awake.vulkan.enums.VkSurfaceTransformFlagBitsKHR, supported: Int = allTransforms) =
        VkSurfaceCapabilitiesKHR(currentExtent = portrait, supportedTransforms = supported, currentTransform = transform)

    @Test
    fun aQuarterTurnLetsTheCompositorRotateAndSizesImagesForTheWindow() {
        for (turn in listOf(VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR, VK_SURFACE_TRANSFORM_ROTATE_270_BIT_KHR)) {
            val (transform, extent) = planSwapchainOrientation(surface(turn), portrait)

            assertEquals(VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR, transform, "$turn must not claim the frame is pre-rotated")
            assertEquals(VkExtent2D(2400, 1080), extent, "$turn must build landscape images")
        }
    }

    @Test
    fun aQuarterTurnWithAlreadyLandscapeExtentDoesNotInvertDimensions() {
        val landscape = VkExtent2D(2400, 1080)
        val landscapeSurface = VkSurfaceCapabilitiesKHR(
            currentExtent = landscape,
            supportedTransforms = allTransforms,
            currentTransform = VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR,
        )
        val (transform, extent) = planSwapchainOrientation(landscapeSurface, landscape)

        assertEquals(VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR, transform)
        assertEquals(landscape, extent, "Landscape extent must not be inverted back to portrait")
    }

    @Test
    fun theNaturalOrientationAndAHalfTurnKeepTheExtent() {
        for (turn in listOf(VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR, VK_SURFACE_TRANSFORM_ROTATE_180_BIT_KHR)) {
            assertEquals(VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR to portrait, planSwapchainOrientation(surface(turn), portrait))
        }
    }

    @Test
    fun aSurfaceWithoutIdentityKeepsItsCurrentTransform() {
        val onlyRotated = surface(VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR, supported = VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR.value)

        assertEquals(VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR to portrait, planSwapchainOrientation(onlyRotated, portrait))
    }

    @Test
    fun aSteadyRotatedSurfaceDoesNotRebuildButATurnDoes() {
        val rotated = surface(VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR)

        assertFalse(surfaceChanged(rotated, VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR, portrait), "suboptimal on every frame must not rebuild")
        assertTrue(surfaceChanged(rotated, VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR, portrait), "a turn must rebuild")
        assertTrue(surfaceChanged(rotated, VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR, VkExtent2D(720, 1600)), "a resize must rebuild")
    }

    @Test
    fun aWindowSizedSurfaceAlwaysRebuildsOnSuboptimal() {
        val desktop = VkSurfaceCapabilitiesKHR(currentExtent = VkExtent2D(Int.MAX_VALUE, Int.MAX_VALUE))

        assertTrue(surfaceChanged(desktop, VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR, VkExtent2D(Int.MAX_VALUE, Int.MAX_VALUE)))
    }
}
