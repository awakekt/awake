/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.quality

import com.awakekt.awake.engine.platform.GraphicsEngine
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.render.renderer.TextureSampling
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.TextureFiltering
import kotlin.test.Test
import kotlin.test.assertEquals

/** [TextureSettings] reaching a renderer through `GraphicsEngine`, and [TextureSampling]'s rules (#1660). */
class TextureSettingsTest {
    @Test
    fun theEngineAppliesTheSelectionBeforeEveryFrame() {
        val settings = TextureSettings()
        val renderer = TextureRenderer(supported = 16)
        val seen = mutableListOf<Pair<Int, Int>>()
        val engine = engineFor(app(settings) { seen += renderer.maxAnisotropy to renderer.textureMipSkip }, renderer)

        engine.update(0f)
        settings.select(TextureAnisotropy.X16)
        settings.select(TextureQuality.Low)
        engine.update(0f)

        assertEquals(listOf(8 to 0, 16 to 2), seen, "the app's frame sees this frame's settings")
    }

    @Test
    fun anAppWithoutTextureSettingsKeepsTheRenderersDefaults() {
        val renderer = TextureRenderer(supported = 16)
        val engine = engineFor(AppSpecBuilder().build().createLifecycle(), renderer)

        engine.update(0f)

        assertEquals(1, renderer.maxAnisotropy, "no anisotropic filtering unless an app asks for it")
        assertEquals(0, renderer.textureMipSkip, "full-size textures unless an app asks otherwise")
        assertEquals(0, renderer.writes, "nothing written to the renderer")
    }

    @Test
    fun anUnchangedSelectionWritesNothing() {
        val settings = TextureSettings()
        val renderer = TextureRenderer(supported = 16)
        settings.applyTo(renderer)
        val after = renderer.writes
        repeat(3) { settings.applyTo(renderer) }

        assertEquals(after, renderer.writes, "a frame with no change writes nothing")
    }

    @Test
    fun aChangedSelectionIsSavedOnceAndLoadedNextRun() {
        val persistence = MemoryPersistence()
        val first = TextureSettings(persistence)
        first.select(TextureQuality.Medium)
        first.select(TextureQuality.Medium)
        first.select(TextureAnisotropy.X2)

        assertEquals(listOf<Any>(TextureQuality.Medium, TextureAnisotropy.X2), persistence.saves)
        val second = TextureSettings(persistence)
        assertEquals(TextureQuality.Medium, second.quality.value)
        assertEquals(TextureAnisotropy.X2, second.anisotropy.value)
    }

    @Test
    fun aMobileDeviceStartsAtMediumAndFourSamples() {
        assertEquals(TextureQuality.Medium, TextureSettings.defaultQuality(GpuDeviceClass.Mobile))
        assertEquals(TextureAnisotropy.X4, TextureSettings.defaultAnisotropy(GpuDeviceClass.Mobile))
        assertEquals(TextureQuality.High, TextureSettings.defaultQuality(GpuDeviceClass.Discrete))
        assertEquals(TextureAnisotropy.X8, TextureSettings.defaultAnisotropy(GpuDeviceClass.Discrete))
    }

    @Test
    fun samplingRulesLeavePixelArtAndShortChainsAlone() {
        assertEquals(4, TextureSampling.anisotropy(16, supported = 4), "clamped to the device")
        assertEquals(1, TextureSampling.anisotropy(8, supported = 0), "a device with none takes 1")
        assertEquals(8, TextureSampling.anisotropyFor(TextureFiltering.Linear, 8))
        assertEquals(1, TextureSampling.anisotropyFor(TextureFiltering.Nearest, 8), "pixel art stays sharp")
        assertEquals(1, TextureSampling.anisotropyFor(TextureFiltering.BaseLevelLinear, 8))
        assertEquals(2, TextureSampling.levelsToSkip(TextureFiltering.Linear, levels = 9, mipSkip = 2))
        assertEquals(1, TextureSampling.levelsToSkip(TextureFiltering.Linear, levels = 2, mipSkip = 2), "one level always remains")
        assertEquals(0, TextureSampling.levelsToSkip(TextureFiltering.Linear, levels = 1, mipSkip = 2), "no chain, no skip")
        assertEquals(0, TextureSampling.levelsToSkip(TextureFiltering.Nearest, levels = 9, mipSkip = 2), "pixel art uploads whole")
        assertEquals(2, TextureSampling.mipSkip(5))
    }

    private fun app(settings: TextureSettings, onFrame: () -> Unit = {}): AwakeAppLifecycle =
        AppSpecBuilder().apply {
            service(TextureSettings::class, settings)
            render { onFrame() }
        }.build().createLifecycle()

    private fun engineFor(app: AwakeAppLifecycle, renderer: TextureRenderer) = FakeEngine(app, renderer).also { it.create("surface") }

    private class FakeEngine(app: AwakeAppLifecycle, private val textures: TextureRenderer) : GraphicsEngine(app) {
        override suspend fun createBackendResources(window: Any) = BackendResources(renderer = textures, viewportSize = { 640f to 360f })

        override fun destroyBackend() = Unit
    }

    /** A renderer that keeps both settings and counts writes, as a backend that implements them does. */
    private class TextureRenderer(private val supported: Int) : NoopRenderer() {
        var writes = 0
        override val maxSupportedAnisotropy: Int get() = supported
        override var maxAnisotropy: Int = 1
            set(value) {
                writes++
                field = TextureSampling.anisotropy(value, supported)
            }
        override var textureMipSkip: Int = 0
            set(value) {
                writes++
                field = TextureSampling.mipSkip(value)
            }
    }

    private class MemoryPersistence : TextureSettingsPersistence {
        val saves = mutableListOf<Any>()
        private var quality: TextureQuality? = null
        private var anisotropy: TextureAnisotropy? = null

        override fun loadQuality(): TextureQuality? = quality

        override fun loadAnisotropy(): TextureAnisotropy? = anisotropy

        override fun saveQuality(quality: TextureQuality) {
            saves += quality
            this.quality = quality
        }

        override fun saveAnisotropy(anisotropy: TextureAnisotropy) {
            saves += anisotropy
            this.anisotropy = anisotropy
        }
    }
}
