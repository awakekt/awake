/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDeviceFeatures
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Guards [selectEnabledDeviceFeatures] so unused Vulkan 1.0 features stay disabled. */
class DeviceFeatureSelectionTest {
    @Test
    fun disablesRobustBufferAccessAndUnusedFeaturesWhilePreservingEngineFeatures() {
        val allSupported = VkPhysicalDeviceFeatures(
            robustBufferAccess = true,
            fullDrawIndexUint32 = true,
            imageCubeArray = true,
            independentBlend = true,
            geometryShader = true,
            tessellationShader = true,
            sampleRateShading = true,
            dualSrcBlend = true,
            logicOp = true,
            multiDrawIndirect = true,
            drawIndirectFirstInstance = true,
            depthClamp = true,
            depthBiasClamp = true,
            fillModeNonSolid = true,
            depthBounds = true,
            wideLines = true,
            largePoints = true,
            alphaToOne = true,
            multiViewport = true,
            samplerAnisotropy = true,
            textureCompressionETC2 = true,
            textureCompressionASTC_LDR = true,
            textureCompressionBC = true,
            occlusionQueryPrecise = true,
            pipelineStatisticsQuery = true,
            vertexPipelineStoresAndAtomics = true,
            fragmentStoresAndAtomics = true,
            shaderFloat64 = true,
            shaderInt64 = true,
            shaderInt16 = true,
            sparseBinding = true,
        )

        val enabled = selectEnabledDeviceFeatures(allSupported)

        assertFalse(enabled.robustBufferAccess, "robustBufferAccess must stay disabled to avoid GPU shader bounds checks")
        assertFalse(enabled.geometryShader)
        assertFalse(enabled.tessellationShader)
        assertFalse(enabled.sampleRateShading)
        assertFalse(enabled.shaderFloat64)
        assertFalse(enabled.shaderInt64)
        assertFalse(enabled.sparseBinding)

        assertTrue(enabled.fullDrawIndexUint32)
        assertTrue(enabled.multiDrawIndirect)
        assertTrue(enabled.depthClamp)
        assertTrue(enabled.fillModeNonSolid)
        assertTrue(enabled.samplerAnisotropy)
        assertTrue(enabled.textureCompressionETC2)
        assertTrue(enabled.textureCompressionASTC_LDR)
        assertTrue(enabled.textureCompressionBC)
    }

    @Test
    fun doesNotEnableUnsupportedFeatures() {
        val noneSupported = VkPhysicalDeviceFeatures()
        val enabled = selectEnabledDeviceFeatures(noneSupported)

        assertFalse(enabled.robustBufferAccess)
        assertFalse(enabled.fullDrawIndexUint32)
        assertFalse(enabled.multiDrawIndirect)
        assertFalse(enabled.depthClamp)
        assertFalse(enabled.fillModeNonSolid)
        assertFalse(enabled.samplerAnisotropy)
        assertFalse(enabled.textureCompressionETC2)
        assertFalse(enabled.textureCompressionASTC_LDR)
        assertFalse(enabled.textureCompressionBC)
    }
}
