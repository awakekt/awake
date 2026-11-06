/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.quality

import com.awakekt.awake.engine.platform.GraphicsEngine
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.render.renderer.RenderQuality
import com.awakekt.awake.render.testing.NoopRenderer
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderQualityEngineTest {

    @Test
    fun theEngineAppliesTheSelectedPresetBeforeEveryFrame() {
        val settings = RenderQualitySettings()
        val renderer = SurfaceRenderer(3840, 2160, GpuDeviceClass.Discrete)
        val scales = mutableListOf<Float>()
        val engine = engineFor(app(settings) { scales += renderer.renderScale }, renderer)

        engine.update(0f)
        settings.select(RenderQuality.Performance)
        engine.update(0f)
        renderer.outputWidth = 1280
        renderer.outputHeight = 720
        engine.update(0f)

        assertEquals(listOf(1440f / 2160f, 540f / 2160f, 540f / 720f), scales, "the app's frame sees this frame's scale")
        assertEquals(EffectQuality.Low, renderer.effectQuality)
    }

    @Test
    fun anAppWithoutRenderQualityKeepsItsOwnScale() {
        val renderer = SurfaceRenderer(3840, 2160, GpuDeviceClass.Discrete).apply { renderScale = 0.8f }
        val engine = engineFor(AppSpecBuilder().build().createLifecycle(), renderer)

        engine.update(0f)

        assertEquals(0.8f, renderer.renderScale)
        assertEquals(EffectQuality.High, renderer.effectQuality)
    }

    @Test
    fun aHostOverrideWinsOverTheSettingsScaleButNotItsEffects() {
        val settings = RenderQualitySettings(initial = RenderQuality.Performance)
        val renderer = SurfaceRenderer(3840, 2160, GpuDeviceClass.Discrete)
        val engine = engineFor(app(settings), renderer)
        var hostScale = 0.9f
        engine.renderScaleOverride = { hostScale }

        engine.update(0f)
        assertEquals(0.9f, renderer.renderScale)
        hostScale = 0.7f
        engine.update(0f)
        assertEquals(0.7f, renderer.renderScale)
        assertEquals(EffectQuality.Low, renderer.effectQuality)

        engine.renderScaleOverride = null
        engine.update(0f)
        assertEquals(540f / 2160f, renderer.renderScale)
    }

    @Test
    fun twoAppsKeepSeparateSettings() {
        val first = RenderQualitySettings()
        val second = RenderQualitySettings()
        val firstRenderer = SurfaceRenderer(2532, 1170, GpuDeviceClass.Mobile)
        val secondRenderer = SurfaceRenderer(2532, 1170, GpuDeviceClass.Mobile)
        val firstApp = app(first)
        val secondApp = app(second)
        val firstEngine = engineFor(firstApp, firstRenderer)
        val secondEngine = engineFor(secondApp, secondRenderer)

        second.select(RenderQuality.High)
        firstEngine.update(0f)
        secondEngine.update(0f)

        assertEquals(RenderQuality.Auto, firstApp.service(RenderQualitySettings::class)?.quality?.value)
        assertEquals(RenderQuality.High, secondApp.service(RenderQualitySettings::class)?.quality?.value)
        assertEquals(900f / 1170f, firstRenderer.renderScale)
        assertEquals(1f, secondRenderer.renderScale)
    }

    @Test
    fun persistenceLoadsTheSavedPresetAndSavesEachChange() {
        val store = MemoryPersistence(saved = RenderQuality.Balanced)
        val settings = RenderQualitySettings(store)
        assertEquals(RenderQuality.Balanced, settings.quality.value)

        settings.select(RenderQuality.Balanced)
        settings.select(RenderQuality.Performance)
        settings.select(RenderQuality.Performance)
        assertEquals(listOf(RenderQuality.Performance), store.saves, "only a change is saved")

        val nextRun = RenderQualitySettings(store)
        assertEquals(RenderQuality.Performance, nextRun.quality.value)
        assertEquals(RenderQuality.High, RenderQualitySettings(MemoryPersistence(null), initial = RenderQuality.High).quality.value)
    }

    private fun app(settings: RenderQualitySettings, onFrame: () -> Unit = {}): AwakeAppLifecycle =
        AppSpecBuilder().apply {
            service(RenderQualitySettings::class, settings)
            render { onFrame() }
        }.build().createLifecycle()

    private fun engineFor(app: AwakeAppLifecycle, renderer: SurfaceRenderer) =
        FakeEngine(app, renderer).also { it.create("surface") }

    private class FakeEngine(app: AwakeAppLifecycle, private val surfaceRenderer: SurfaceRenderer) : GraphicsEngine(app) {
        override suspend fun createBackendResources(window: Any) = BackendResources(
            renderer = surfaceRenderer,
            viewportSize = { surfaceRenderer.outputWidth.toFloat() to surfaceRenderer.outputHeight.toFloat() },
        )

        override fun destroyBackend() = Unit
    }

    private class SurfaceRenderer(
        override var outputWidth: Int,
        override var outputHeight: Int,
        override val deviceClass: GpuDeviceClass,
    ) : NoopRenderer() {
        override var renderScale: Float = 1f
    }

    private class MemoryPersistence(var saved: RenderQuality?) : RenderQualityPersistence {
        val saves = mutableListOf<RenderQuality>()
        override fun load(): RenderQuality? = saved
        override fun save(quality: RenderQuality) {
            saves += quality
            saved = quality
        }
    }
}
