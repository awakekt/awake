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
}
