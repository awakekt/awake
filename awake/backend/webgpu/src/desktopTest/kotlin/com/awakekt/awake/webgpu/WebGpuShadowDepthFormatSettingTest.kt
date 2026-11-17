/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.engine.platform.quality.RenderQualitySettings
import com.awakekt.awake.render.renderer.ShadowDepthFormat
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An app's `renderQuality { shadowDepthFormat }` reaches WebGPU's shadow array as it reaches Vulkan's
 * (#1709): the engine builds the array in the app's format, over its own `shadowDepthFormat`, and
 * keeps its own when the app sets none.
 */
class WebGpuShadowDepthFormatSettingTest {
    @Test
    fun theAppsFormatWinsOverTheEngines() {
        val settings = RenderQualitySettings().apply { shadowDepthFormat = ShadowDepthFormat.Depth16Unorm }
        assertEquals(ShadowDepthFormat.Depth16Unorm, built(app(settings), engineDefault = ShadowDepthFormat.Depth32Float))
    }

    @Test
    fun withoutOneTheEngineKeepsItsOwn() {
        assertEquals(ShadowDepthFormat.Depth16Unorm, built(app(RenderQualitySettings()), engineDefault = ShadowDepthFormat.Depth16Unorm))
    }

    private fun app(settings: RenderQualitySettings): AwakeAppLifecycle = AppSpecBuilder().apply {
        window {
            title = "webgpu-shadow-depth-format"
            size(1, 1)
        }
        service(RenderQualitySettings::class, settings)
    }.build().createLifecycle()

    /** The format the booted renderer's shadow array is in, for an engine whose own setting is [engineDefault]. */
    private fun built(app: AwakeAppLifecycle, engineDefault: ShadowDepthFormat): ShadowDepthFormat = runBlocking {
        val engine = HeadlessWebGpuEngine(app, EngineShowcaseRenderPlan).apply { shadowDepthFormat = engineDefault }
        try {
            engine.boot(HeadlessSurface(1, 1)).shadowDepthFormat
        } finally {
            engine.destroy()
        }
    }
}
