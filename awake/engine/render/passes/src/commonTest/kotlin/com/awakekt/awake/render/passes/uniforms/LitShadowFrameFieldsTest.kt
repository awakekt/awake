/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuShadowCascadeData
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * The frame's share of the lit-shadow block is packed once and copied into each draw. Every draw
 * must still come out exactly as a full per-draw pack would, including when the frame changes.
 */
class LitShadowFrameFieldsTest {
    @Test
    fun eachDrawMatchesAFullPackAcrossDrawsAndFrames() {
        val eye = Vec3f(1f, 2f, 3f)
        val forward = Vec3f(0f, 0f, -1f)
        var light = FloatArray(MaterialUniformLayouts.SceneLight.total) { it * 0.5f }
        var cascades = cascades(1f)
        repeat(FRAMES) { frame ->
            repeat(DRAWS) { draw ->
                val model = Mat4().translate(draw.toFloat(), frame.toFloat(), 0f)
                val mvp = Mat4().translate(0f, draw.toFloat(), 1f)
                val extras = floatArrayOf(draw * 0.1f, 0.5f)
                val animation = Vec3f(draw.toFloat(), 0f, 1f)
                val fog = Color(0.1f * frame, 0.2f, 0.3f, 1f)
                val exposure = 1f + frame
                assertContentEquals(
                    packLitShadowBlock(
                        model, extras, animation, frame.toFloat(), mvp,
                        light, cascades, eye, fog, 0.01f * frame, forward, GpuDebugView.Off, exposure,
                    ),
                    gpuLitShadowUniforms(
                        transform = model,
                        extraUniformFloats = extras,
                        vertexAnimation = animation,
                        timeSeconds = frame.toFloat(),
                        mvp = mvp,
                        lightPayload = light,
                        cascades = cascades,
                        cameraEye = eye,
                        fogColor = fog,
                        fogDensity = 0.01f * frame,
                        cameraForward = forward,
                        exposure = exposure,
                    ),
                    "frame $frame draw $draw",
                )
            }
            // A new frame: new light block and cascades, and a camera that moved in place.
            light = light.copyOf().also { it[0] += 1f }
            cascades = cascades(frame + 2f)
            eye.x += 1f
            forward.y += 0.1f
        }
    }

    private fun cascades(split: Float) = GpuShadowCascadeData(
        viewProjections = listOf(Mat4().translate(split, 0f, 0f), Mat4()),
        splitDistances = floatArrayOf(split, split * 2f),
    )

    private companion object {
        const val FRAMES = 3
        const val DRAWS = 4
    }
}
