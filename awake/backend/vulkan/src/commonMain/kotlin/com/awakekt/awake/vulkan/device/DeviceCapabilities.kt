/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.vulkan.Version

/**
 * Hardware capabilities, negotiated Vulkan API version, and supported feature paths
 * for the active [GraphicsDevice].
 *
 * Populated during device initialization and queried by the renderer and pipelines
 * to select modern fast-paths (dynamic rendering, synchronization2, timeline semaphores,
 * push descriptors) or fallback paths on older drivers.
 *
 * @property apiVersion The highest Vulkan API version supported by the physical device driver.
 * @property driverVersion The vendor-specific driver version integer.
 * @property deviceName The human-readable name of the physical device.
 * @property deviceClass Coarse hardware classification reported by the physical device.
 * @property dynamicRendering True if dynamic rendering (`VkRenderingInfo` / no `VkRenderPass`) is supported.
 * @property synchronization2 True if synchronization2 (`vkCmdPipelineBarrier2` / `vkQueueSubmit2`) is supported.
 * @property timelineSemaphores True if timeline semaphores are supported.
 * @property extendedDynamicState True if extended dynamic state (cull mode, depth test at draw time) is supported.
 * @property extendedDynamicState2 True if extended dynamic state 2 is supported.
 * @property descriptorIndexing True if descriptor indexing (bindless) is supported.
 * @property bufferDeviceAddress True if buffer device address (GPU pointers) is supported.
 * @property drawIndirectCount True if GPU-decided indirect draw count is supported.
 * @property pipelineCreationCacheControl True if background pipeline cache control is supported.
 * @property pushDescriptors True if push descriptors (per-draw resources in cmd buffer) are supported.
 * @property dynamicRenderingLocalRead True if dynamic rendering local read (on-chip attachment reads) is supported.
 * @property maintenance4 True if maintenance4 (pipeline creation cache control, local workgroup queries) is supported.
 * @property maintenance5 True if maintenance5 (SPIR-V straight in, bind index buffer with size) is supported.
 * @property maintenance6 True if maintenance6 (`vkCmdBindDescriptorSets2` / `vkCmdPushConstants2`) is supported.
 * @property scalarBlockLayout True if scalar block layout (std430 without padding) is supported.
 * @property hostImageCopy True if host image copy (direct CPU texture upload) is supported.
 * @property lineRasterization True if wide, smooth, or stippled line rasterization is supported.
 * @property enabledExtensions The set of device extension names enabled on the logical device.
 */
data class DeviceCapabilities(
    val apiVersion: Version,
    val driverVersion: Int,
    val deviceName: String,
    val deviceClass: GpuDeviceClass,
    val dynamicRendering: Boolean,
    val synchronization2: Boolean,
    val timelineSemaphores: Boolean,
    val extendedDynamicState: Boolean,
    val extendedDynamicState2: Boolean,
    val descriptorIndexing: Boolean,
    val bufferDeviceAddress: Boolean,
    val drawIndirectCount: Boolean,
    val pipelineCreationCacheControl: Boolean,
    val pushDescriptors: Boolean,
    val dynamicRenderingLocalRead: Boolean,
    val maintenance4: Boolean,
    val maintenance5: Boolean,
    val maintenance6: Boolean,
    val scalarBlockLayout: Boolean,
    val hostImageCopy: Boolean,
    val lineRasterization: Boolean,
    val enabledExtensions: Set<String>,
) {
    /** True if the device API version is at least Vulkan 1.3. */
    val isVulkan13OrHigher: Boolean
        get() = apiVersion >= Version(1, 3, 0)

    /** True if the device API version is at least Vulkan 1.4. */
    val isVulkan14OrHigher: Boolean
        get() = apiVersion >= Version(1, 4, 0)

    /**
     * Factory defaults and sentinel constants for [DeviceCapabilities].
     */
    companion object {
        /** Fallback uninitialized capabilities before device creation. */
        internal val Uninitialized = DeviceCapabilities(
            apiVersion = Version(1, 0, 0),
            driverVersion = 0,
            deviceName = "Uninitialized",
            deviceClass = GpuDeviceClass.Unknown,
            dynamicRendering = false,
            synchronization2 = false,
            timelineSemaphores = false,
            extendedDynamicState = false,
            extendedDynamicState2 = false,
            descriptorIndexing = false,
            bufferDeviceAddress = false,
            drawIndirectCount = false,
            pipelineCreationCacheControl = false,
            pushDescriptors = false,
            dynamicRenderingLocalRead = false,
            maintenance4 = false,
            maintenance5 = false,
            maintenance6 = false,
            scalarBlockLayout = false,
            hostImageCopy = false,
            lineRasterization = false,
            enabledExtensions = emptySet(),
        )
    }
}
