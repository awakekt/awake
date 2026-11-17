/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.quality

import com.awakekt.awake.engine.platform.GraphicsEngine
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.renderer.ShadowDepthFormat
import com.awakekt.awake.render.testing.NoopRenderer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An app picks its shadow maps' depth format through `renderQuality { shadowDepthFormat }` (#1709):
 * the engine builds them in it as it builds its GPU resources, and keeps its own setting when the
 * app sets none or registers no render quality at all.
 */
class ShadowDepthFormatSettingTest {
    @Test
    fun theAppsFormatWinsOverTheEngines() {
        val settings = RenderQualitySettings().apply { shadowDepthFormat = ShadowDepthFormat.Depth16Unorm }
        assertEquals(ShadowDepthFormat.Depth16Unorm, built(app(settings), engineDefault = ShadowDepthFormat.Depth32Float))
    }

    @Test
    fun withoutOneTheEngineKeepsItsOwn() {
        assertEquals(ShadowDepthFormat.Depth16Unorm, built(app(RenderQualitySettings()), engineDefault = ShadowDepthFormat.Depth16Unorm))
        assertEquals(ShadowDepthFormat.Depth32Float, built(app(null), engineDefault = ShadowDepthFormat.Depth32Float))
    }

    private fun app(settings: RenderQualitySettings?): AwakeAppLifecycle = AppSpecBuilder().apply {
        if (settings != null) service(RenderQualitySettings::class, settings)
        render { }
    }.build().createLifecycle()

    /** The format an engine whose own setting is [engineDefault] builds its shadow maps in. */
    private fun built(app: AwakeAppLifecycle, engineDefault: ShadowDepthFormat): ShadowDepthFormat {
        val engine = FormatEngine(app, engineDefault)
        engine.create("surface")
        return engine.built
    }

    private class FormatEngine(app: AwakeAppLifecycle, private val engineDefault: ShadowDepthFormat) : GraphicsEngine(app) {
        var built: ShadowDepthFormat = ShadowDepthFormat.Default

        override suspend fun createBackendResources(window: Any): BackendResources {
            built = shadowDepthFormatFor(engineDefault)
            return BackendResources(renderer = NoopRenderer(), viewportSize = { 640f to 360f })
        }

        override fun destroyBackend() = Unit
    }
}
