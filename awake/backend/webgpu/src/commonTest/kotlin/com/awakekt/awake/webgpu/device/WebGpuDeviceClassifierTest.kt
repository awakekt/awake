/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.render.pipeline.recommendedEffectQuality
import com.awakekt.awake.render.renderer.RenderQuality
import com.awakekt.awake.render.renderer.RenderQualityPolicy
import com.awakekt.awake.render.renderer.RenderScale
import kotlin.test.Test
import kotlin.test.assertEquals

/** The WebGPU device classification table (#1365), and what the class it picks drives. */
class WebGpuDeviceClassifierTest {

    @Test
    fun aPhoneIsMobile() {
        assertEquals(GpuDeviceClass.Mobile, classifyWebGpuDevice(PHONE_GPU, PHONE))
        // The hint decides even when the browser masks the adapter.
        assertEquals(GpuDeviceClass.Mobile, classifyWebGpuDevice(WebGpuAdapterReport(), PHONE))
        assertEquals(GpuDeviceClass.Mobile, classifyWebGpuDevice(null, HostMobileHint(userAgentMobile = true)))
    }

    @Test
    fun aTabletIsMobile() {
        // A tablet's browser does not call itself mobile, but its primary pointer is a finger.
        val tablet = HostMobileHint(coarsePointer = true, userAgentMobile = false)
        assertEquals(GpuDeviceClass.Mobile, classifyWebGpuDevice(WebGpuAdapterReport(vendor = "apple"), tablet))
    }

    @Test
    fun aMobileGpuWithNoHintIsMobile() {
        assertEquals(GpuDeviceClass.Mobile, classifyWebGpuDevice(PHONE_GPU, HostMobileHint.None))
    }

    @Test
    fun aDesktopDiscreteGpuIsDiscrete() {
        val nvidia = WebGpuAdapterReport(vendor = "nvidia", architecture = "ampere")
        val amd = WebGpuAdapterReport(vendor = "amd", architecture = "rdna-3")
        assertEquals(GpuDeviceClass.Discrete, classifyWebGpuDevice(nvidia, DESKTOP))
        assertEquals(GpuDeviceClass.Discrete, classifyWebGpuDevice(amd, DESKTOP))
        // wgpu-native, with no browser: the adapter alone decides.
        assertEquals(GpuDeviceClass.Discrete, classifyWebGpuDevice(nvidia, HostMobileHint.None))
    }

    @Test
    fun aDesktopIntegratedGpuIsIntegrated() {
        val intel = WebGpuAdapterReport(vendor = "intel", architecture = "gen-12lp")
        val apple = WebGpuAdapterReport(vendor = "apple", architecture = "metal-3")
        assertEquals(GpuDeviceClass.Integrated, classifyWebGpuDevice(intel, DESKTOP))
        assertEquals(GpuDeviceClass.Integrated, classifyWebGpuDevice(apple, HostMobileHint.None))
        // An ARM laptop: a mobile GPU vendor, in a browser that says it is not mobile.
        assertEquals(GpuDeviceClass.Integrated, classifyWebGpuDevice(WebGpuAdapterReport(vendor = "qualcomm"), DESKTOP))
        // A desktop browser that masks the adapter still says it is not mobile.
        assertEquals(GpuDeviceClass.Integrated, classifyWebGpuDevice(WebGpuAdapterReport(), DESKTOP))
    }

    @Test
    fun aFallbackAdapterIsSoftware() {
        val fallback = WebGpuAdapterReport(vendor = "google", isFallbackAdapter = true)
        assertEquals(GpuDeviceClass.Software, classifyWebGpuDevice(fallback, DESKTOP))
        // Software wins over the mobile hint: a CPU rasterizer on a phone is still a CPU rasterizer.
        assertEquals(GpuDeviceClass.Software, classifyWebGpuDevice(fallback, PHONE))
        val swiftShader = WebGpuAdapterReport(vendor = "google", architecture = "swiftshader")
        assertEquals(GpuDeviceClass.Software, classifyWebGpuDevice(swiftShader, HostMobileHint.None))
        val lavapipe = WebGpuAdapterReport(vendor = "mesa", device = "llvmpipe (LLVM 19.1.7, 256 bits)")
        assertEquals(GpuDeviceClass.Software, classifyWebGpuDevice(lavapipe, HostMobileHint.None))
    }

    @Test
    fun nothingKnownStaysUnknown() {
        assertEquals(GpuDeviceClass.Unknown, classifyWebGpuDevice(null, HostMobileHint.None))
        assertEquals(GpuDeviceClass.Unknown, classifyWebGpuDevice(WebGpuAdapterReport(), HostMobileHint.None))
        // A vendor it does not know, and no hint, is not guessed at.
        assertEquals(GpuDeviceClass.Unknown, classifyWebGpuDevice(WebGpuAdapterReport(vendor = "vendor-x"), HostMobileHint.None))
    }

    @Test
    fun renderQualityAutoUsesTheMobileBudgetOnAClassifiedPhone() {
        val deviceClass = classifyWebGpuDevice(PHONE_GPU, PHONE)
        // An 844 by 390 CSS-pixel viewport at a device pixel ratio of 3.
        val scale = RenderQualityPolicy.renderScale(RenderQuality.Auto, 2532, 1170, deviceClass)
        assertEquals(900, RenderScale.sceneExtent(1170, scale), "the scene's short edge on a phone")
        // Unclassified, the same phone rendered at the Unknown budget: 1080 tall.
        val unknown = RenderQualityPolicy.renderScale(RenderQuality.Auto, 2532, 1170, GpuDeviceClass.Unknown)
        assertEquals(1080, RenderScale.sceneExtent(1170, unknown))
        assertEquals(EffectQuality.Low, RenderQualityPolicy.effectQuality(RenderQuality.Auto, deviceClass))
    }

    @Test
    fun effectQualityDefaultsFromTheClass() {
        // The renderer's default is the class's recommendation (`Renderer.effectQuality`).
        val expected = mapOf(
            classifyWebGpuDevice(PHONE_GPU, PHONE) to EffectQuality.Low,
            classifyWebGpuDevice(WebGpuAdapterReport(vendor = "nvidia"), DESKTOP) to EffectQuality.High,
            classifyWebGpuDevice(WebGpuAdapterReport(vendor = "intel"), DESKTOP) to EffectQuality.Medium,
            classifyWebGpuDevice(WebGpuAdapterReport(isFallbackAdapter = true), DESKTOP) to EffectQuality.Low,
            classifyWebGpuDevice(null, HostMobileHint.None) to EffectQuality.Medium,
        )
        assertEquals(5, expected.size, "each row is a different class")
        expected.forEach { (deviceClass, quality) -> assertEquals(quality, deviceClass.recommendedEffectQuality(), "$deviceClass") }
    }

    private companion object {
        val PHONE_GPU = WebGpuAdapterReport(vendor = "arm", architecture = "valhall")
        val PHONE = HostMobileHint(coarsePointer = true, userAgentMobile = true)
        val DESKTOP = HostMobileHint(coarsePointer = false, userAgentMobile = false)
    }
}
