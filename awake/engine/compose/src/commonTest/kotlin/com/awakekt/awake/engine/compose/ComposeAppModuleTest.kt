/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.compose

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.renderer.DrawCall
import com.awakekt.awake.render.renderer.SceneLight
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ComposeAppModuleTest {
    @Test
    fun stagesUiAndPresentsUiOnlyFrame() = runTest {
        val builder = AppSpecBuilder()
        composeAppModule(content = {}).install(builder)
        val lifecycle = builder.build().createLifecycle()
        val renderer = RecordingRenderer()

        lifecycle.ready(renderer)
        lifecycle.update(1f / 60f, 320f, 240f)

        assertNotNull(lifecycle.service(ComposeAppRuntime::class))
        assertEquals(1, renderer.uiDraws)
        assertEquals(1, renderer.presents)
    }

    @Test
    fun rejectsSecondComposeHost() {
        val builder = AppSpecBuilder()
        composeAppModule(content = {}).install(builder)

        assertFailsWith<IllegalStateException> {
            composeAppModule(content = {}).install(builder)
        }
    }
}

private class RecordingRenderer : NoopRenderer() {
    var uiDraws = 0
    var presents = 0

    override fun drawUi(
        primitives: List<com.awakekt.awake.core.graphics2d.UiDrawPrimitive>,
        font: UiFont?,
    ) {
        uiDraws += 1
    }

    override fun draw(camera: Lens, drawCalls: List<DrawCall>, light: SceneLight) {
        presents += 1
    }
}
