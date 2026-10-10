/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.renderer.UniformFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderDebugViewTest {

    @Test
    fun offLowersToTheCanonicalOffAndKeepsTheSky() {
        val state = EnvironmentUniforms(showSky = true).toGpuState(viewDepthRange = 50f)

        assertEquals(GpuDebugView.Off, state.debugView)
        assertTrue(state.showSky)
    }

    @Test
    fun aViewCarriesItsCodeRangeAndLayerAndHidesTheSky() {
        val state = EnvironmentUniforms(showSky = true, debugView = RenderDebugView.ShadowMap, debugLayer = 3)
            .toGpuState(viewDepthRange = 50f)

        assertEquals(GpuDebugView(RenderDebugView.ShadowMap.code, 50f, 3), state.debugView)
        assertFalse(state.showSky)
    }

    @Test
    fun theLitBlockEndsWithTheForwardOverTheRangeThenTheCode() {
        val block = gpuLitShadowUniforms(
            transform = Mat4(),
            extraUniformFloats = FloatArray(0),
            vertexAnimation = Vec3f(0f, 0f, 0f),
            timeSeconds = 0f,
            mvp = Mat4(),
            lightPayload = FloatArray(0),
            cascades = ShadowCascadeUniforms.UNSHADOWED,
            cameraEye = Vec3f(0f, 0f, 0f),
            cameraForward = Vec3f(0f, 0f, -1f),
            debugView = GpuDebugView(RenderDebugView.LinearDepth.code, depthRange = 4f),
        )
        val at = MaterialUniformLayouts.LitShadow.offsetOf(UniformFields.DebugView)

        assertEquals(listOf(0f, 0f, -0.25f, RenderDebugView.LinearDepth.code.toFloat()), block.slice(at until at + 4))
    }

    /** Layer 0 red, 1 green, 2 violet: the palette the shaders' layer and joint views paint, on the CPU. */
    @Test
    fun theDebugLayerColoursStartRedGreenViolet() {
        assertNear(listOf(1f, 0.25f, 0.25f), debugLayerColor(0))
        assertNear(listOf(0.131f, 0.977f, 0.392f), debugLayerColor(1))
        assertNear(listOf(0.544f, 0.047f, 0.909f), debugLayerColor(2))
        assertEquals(1f, debugLayerColor(7).a)
    }

    private fun assertNear(expected: List<Float>, color: com.awakekt.awake.core.color.Color) {
        val actual = listOf(color.r, color.g, color.b)
        assertTrue(expected.zip(actual).all { (e, a) -> kotlin.math.abs(e - a) < 0.002f }, "expected $expected, was $actual")
    }
}
