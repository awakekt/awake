/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.quality.RenderQualitySettings
import com.awakekt.awake.render.renderer.RenderQuality
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** A scene overlay reads the app's render quality setting through [LocalRenderQuality] and changes it. */
class SceneRenderQualityLocalTest {

    private fun sceneSpec(ui: SceneContent) = SceneAppSpec(
        sceneName = null,
        systems = emptyList(),
        scenePopulationBlock = {},
        renderableFactory = { error("no renderable requested in this test") },
        assetLibraryFactory = null,
        updateBlock = { _, _ -> },
        ui = ui,
        onReadyBlock = {},
        onDisposeBlock = {},
        serviceRegistrations = emptyList(),
        infrastructureSystemsFactory = { emptyList() },
    )

    @Test
    fun anOverlayReadsAndChangesTheAppsPreset() = runTest {
        val settings = RenderQualitySettings()
        var seenSettings: RenderQualitySettings? = null
        val seenPresets = mutableListOf<RenderQuality>()
        val app = AppSpecBuilder().apply {
            service(RenderQualitySettings::class, settings)
            install(
                sceneSpec {
                    val quality = LocalRenderQuality.current
                    seenSettings = quality
                    if (quality != null) {
                        seenPresets += quality.quality.value
                        quality.select(RenderQuality.Performance)
                    }
                },
            )
        }.build().createLifecycle()

        app.ready(NoopRenderer())
        app.update(0.016f, 320f, 240f)
        app.update(0.016f, 320f, 240f)

        assertSame(settings, seenSettings, "the overlay sees this app's own instance")
        assertEquals(listOf(RenderQuality.Auto, RenderQuality.Performance), seenPresets.take(2))
        assertEquals(RenderQuality.Performance, settings.quality.value)
    }

    @Test
    fun anAppThatDidNotOptInProvidesNull() = runTest {
        var reads = 0
        var seen: RenderQualitySettings? = RenderQualitySettings()
        val app = AppSpecBuilder().apply {
            install(
                sceneSpec {
                    reads++
                    seen = LocalRenderQuality.current
                },
            )
        }.build().createLifecycle()

        app.ready(NoopRenderer())
        app.update(0.016f, 320f, 240f)

        assertEquals(true, reads > 0)
        assertNull(seen)
    }
}
