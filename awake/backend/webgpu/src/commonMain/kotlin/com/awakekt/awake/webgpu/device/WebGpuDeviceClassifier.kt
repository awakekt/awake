/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

import com.awakekt.awake.render.pipeline.GpuDeviceClass
import io.ygdrasil.webgpu.GPUAdapter

/**
 * What the adapter reports about itself, from `GPUAdapter.info`. A browser may leave any string
 * empty, and often leaves all of them empty.
 */
internal data class WebGpuAdapterReport(
    val vendor: String = "",
    val architecture: String = "",
    val device: String = "",
    val description: String = "",
    val isFallbackAdapter: Boolean = false,
)

/**
 * The host's hint about the form factor, read once at device creation.
 *
 * @property coarsePointer `matchMedia('(pointer: coarse)')` matched: the primary pointer is a finger,
 *   as on a phone or a tablet. False when it did not match, or when there is no browser.
 * @property userAgentMobile `navigator.userAgentData.mobile`, or null where the browser has no
 *   `userAgentData` (only Chromium-based browsers have it).
 */
internal data class HostMobileHint(
    val coarsePointer: Boolean = false,
    val userAgentMobile: Boolean? = null,
) {
    companion object {
        /** No hint: a host that is not a browser, or a browser that says nothing. */
        val None: HostMobileHint = HostMobileHint()
    }
}

/** The host's [HostMobileHint]: the browser's on wasm, [HostMobileHint.None] elsewhere. */
internal expect fun hostMobileHint(): HostMobileHint

/**
 * [GPUAdapter.info] as a [WebGpuAdapterReport], or null when the adapter cannot report it: a browser
 * older than `GPUAdapter.info` (Chrome 127) leaves it undefined, and reading it then throws.
 */
internal fun GPUAdapter.report(): WebGpuAdapterReport? = runCatching {
    info.let { WebGpuAdapterReport(it.vendor, it.architecture, it.device, it.description, it.isFallbackAdapter) }
}.getOrNull()

/**
 * Classifies a WebGPU adapter with the meaning the Vulkan backend gives [GpuDeviceClass] from
 * `VkPhysicalDeviceType`, using what a browser can report instead of a device type, which WebGPU
 * does not expose.
 *
 * In order, first match wins:
 * 1. A fallback adapter, or one named after a CPU rasterizer (SwiftShader, llvmpipe, lavapipe,
 *    WARP), is [GpuDeviceClass.Software], as Vulkan's `VK_PHYSICAL_DEVICE_TYPE_CPU` is.
 * 2. A coarse pointer or `userAgentData.mobile` is [GpuDeviceClass.Mobile]: a phone or a tablet,
 *    whatever its vendor.
 * 3. An NVIDIA or AMD adapter is [GpuDeviceClass.Discrete]; Intel and Apple are
 *    [GpuDeviceClass.Integrated].
 * 4. A mobile GPU vendor (ARM, Qualcomm, Imagination, Samsung) is [GpuDeviceClass.Mobile], unless
 *    the browser says it is not mobile, as on an ARM laptop: then [GpuDeviceClass.Integrated].
 * 5. Any other adapter is [GpuDeviceClass.Integrated] when the browser says it is not mobile, the
 *    common desktop case, and [GpuDeviceClass.Unknown] otherwise: nothing known stays unknown.
 *
 * A fine pointer alone is not a desktop hint: a browser that cannot evaluate the query reports one.
 */
internal fun classifyWebGpuDevice(report: WebGpuAdapterReport?, hint: HostMobileHint): GpuDeviceClass {
    val vendor = report?.vendor.orEmpty().lowercase()
    val desktop = hint.userAgentMobile == false
    return when {
        report != null && report.isSoftware() -> GpuDeviceClass.Software
        hint.coarsePointer || hint.userAgentMobile == true -> GpuDeviceClass.Mobile
        DISCRETE_VENDORS.any { it in vendor } -> GpuDeviceClass.Discrete
        INTEGRATED_VENDORS.any { it in vendor } -> GpuDeviceClass.Integrated
        MOBILE_VENDORS.any { it in vendor } -> if (desktop) GpuDeviceClass.Integrated else GpuDeviceClass.Mobile
        desktop -> GpuDeviceClass.Integrated
        else -> GpuDeviceClass.Unknown
    }
}

private fun WebGpuAdapterReport.isSoftware(): Boolean {
    if (isFallbackAdapter) return true
    val names = listOf(vendor, architecture, device, description).joinToString(" ").lowercase()
    return SOFTWARE_RASTERIZERS.any { it in names }
}

/** Names the browsers and wgpu-native give CPU rasterizers, lower case. */
private val SOFTWARE_RASTERIZERS = listOf("swiftshader", "llvmpipe", "lavapipe", "softpipe", "warp", "basic render")

private val DISCRETE_VENDORS = listOf("nvidia", "amd", "ati technologies")

private val INTEGRATED_VENDORS = listOf("intel", "apple")

private val MOBILE_VENDORS = listOf("arm", "qualcomm", "imagination", "img-tec", "samsung", "mali", "adreno", "powervr")
