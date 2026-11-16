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
import com.awakekt.awake.render.renderer.ShadowBudget
import com.awakekt.awake.render.testing.NoopRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertEquals(ShadowBudget.Reduced, renderer.shadowBudget, "Performance draws two cascades of 1024")
    }

    @Test
    fun aPhoneUnderAutoDrawsReducedShadowsUnlessTheAppKeepsItsOwn() {
        val settings = RenderQualitySettings()
        val phone = SurfaceRenderer(2400, 1080, GpuDeviceClass.Mobile)
        val engine = engineFor(app(settings), phone)
        engine.update(0f)
        assertEquals(ShadowBudget.Reduced, phone.shadowBudget)

        settings.shadowBudgetOverride = ShadowBudget.Default
        engine.update(0f)
        assertEquals(ShadowBudget.Default, phone.shadowBudget, "the override keeps the shadows drawn before #1688")
    }

    /** #1688: a phone whose frames run long under Auto draws fewer pixels; a fixed preset does not move. */
    @Test
    fun autoLowersThePhonesResolutionWhileFramesRunLong() {
        val settings = RenderQualitySettings()
        val phone = SurfaceRenderer(2400, 1080, GpuDeviceClass.Mobile)
        val engine = engineFor(app(settings), phone)
        engine.update(0.016f)
        assertEquals(720f / 1080f, phone.renderScale, "it starts at Auto's budget")

        // 25 fps at Auto's budget, with frame time following the pixels drawn.
        val gpuBound = { 0.04f * (phone.renderScale * 1080f / 720f) * (phone.renderScale * 1080f / 720f) }
        repeat(120) { engine.update(gpuBound()) }
        assertTrue(phone.renderScale < 720f / 1080f, "25 fps against 60 lowers it: ${phone.renderScale}")
        assertTrue(phone.renderScale >= 540f / 1080f, "never below the floor")

        settings.select(RenderQuality.Balanced)
        repeat(120) { engine.update(0.04f) }
        assertEquals(720f / 1080f, phone.renderScale, "a preset is a fixed budget")
    }

    @Test
    fun withoutDynamicResolutionAutoKeepsItsBudget() {
        val settings = RenderQualitySettings().apply { dynamicResolution = null }
        val phone = SurfaceRenderer(2400, 1080, GpuDeviceClass.Mobile)
        val engine = engineFor(app(settings), phone)
        repeat(120) { engine.update(0.04f) }
        assertEquals(720f / 1080f, phone.renderScale)
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
        assertEquals(720f / 1170f, firstRenderer.renderScale)
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
        override var shadowBudget: ShadowBudget = ShadowBudget.Default
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
