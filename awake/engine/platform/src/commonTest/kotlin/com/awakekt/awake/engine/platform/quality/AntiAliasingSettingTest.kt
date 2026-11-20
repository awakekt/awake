/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.quality

import com.awakekt.awake.engine.platform.GraphicsEngine
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.pipeline.MultisampleState
import com.awakekt.awake.render.renderer.AntiAliasing
import com.awakekt.awake.render.testing.NoopRenderer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An app picks the scene's anti-aliasing through `renderQuality { antiAliasing }` (#1791): the engine
 * builds its scene pass for that mode as it builds its GPU resources, and keeps the render plan's
 * sample count when the app sets none or registers no render quality at all.
 */
class AntiAliasingSettingTest {
    private val planAt4x = MultisampleState(count = 4, alphaToCoverageEnabled = true)

    @Test
    fun theAppsModeWinsOverThePlans() {
        assertEquals(AntiAliasing.Fxaa, built(app(RenderQualitySettings().apply { antiAliasing = AntiAliasing.Fxaa }), planAt4x))
        assertEquals(
            AntiAliasing.Msaa2x,
            built(app(RenderQualitySettings().apply { antiAliasing = AntiAliasing.Msaa2x }), MultisampleState.Single),
        )
    }

    @Test
    fun withoutOneThePlansSampleCountStands() {
        assertEquals(AntiAliasing.Msaa4x, built(app(RenderQualitySettings()), planAt4x))
        assertEquals(AntiAliasing.Off, built(app(null), MultisampleState.Single))
    }

    private fun app(settings: RenderQualitySettings?): AwakeAppLifecycle = AppSpecBuilder().apply {
        if (settings != null) service(RenderQualitySettings::class, settings)
        render { }
    }.build().createLifecycle()

    /** The mode an engine whose render plan rasterizes at [plan] builds its scene pass for. */
    private fun built(app: AwakeAppLifecycle, plan: MultisampleState): AntiAliasing {
        val engine = ModeEngine(app, plan)
        engine.create("surface")
        return engine.built
    }

    private class ModeEngine(app: AwakeAppLifecycle, private val plan: MultisampleState) : GraphicsEngine(app) {
        var built: AntiAliasing = AntiAliasing.Off

        override suspend fun createBackendResources(window: Any): BackendResources {
            built = antiAliasingFor(plan)
            return BackendResources(renderer = NoopRenderer(), viewportSize = { 640f to 360f })
        }

        override fun destroyBackend() = Unit
    }
}
