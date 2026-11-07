/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A draw packed from the inputs its slot last took, in a frame like the last, is recognised as such,
 * and any input its block is packed from tells it apart (#1452).
 *
 * Negative control, measured: with the clock compared as given rather than as the packers write it,
 * [aStillDrawMatchesAtALaterClock] failed, so no static draw would ever skip its packing.
 */
class DrawUniformInputsTest {
    @Test
    fun aFrameRebuiltTheSameKeepsItsVersion() {
        val frame = FrameUniformInputs()
        val first = frame.update(frameOf())

        // New objects with the same values, as the scene compiler builds them every frame.
        assertEquals(first, frame.update(frameOf()))
        assertNotEquals(first, frame.update(frameOf(eye = Vec3f(0f, 4f, 7f))), "the camera moved")
        val moved = frame.version
        assertNotEquals(moved, frame.update(frameOf(eye = Vec3f(0f, 4f, 7f), split = 21f)), "a cascade split moved")
        assertNotEquals(frame.version, frame.update(frameOf(eye = Vec3f(0f, 4f, 7f), split = 21f, fog = 0.1f)), "the fog thickened")
    }

    @Test
    fun aStillDrawMatchesAtALaterClock() {
        val inputs = DrawUniformInputs()
        inputs.record(draw(time = 1f), FRAME, COUNT, false)

        assertTrue(inputs.matches(draw(time = 2f), FRAME, COUNT, false))
        assertFalse(inputs.matches(draw(time = 2f), FRAME + 1, COUNT, false), "the frame's share changed")
    }

    @Test
    fun everyInputItsBlockIsPackedFromTellsADrawApart() {
        val inputs = DrawUniformInputs()
        inputs.record(draw(), FRAME, COUNT, false)

        val changed = mapOf(
            "model" to draw(x = 1f),
            "extras" to draw(extras = pbr(roughness = 0.5f)),
            "wave" to draw(wave = Vec3f(0.2f, 1f, 1f)),
            "fade" to draw().apply { fadeOpacity = 0.5f },
            "cutoff" to draw().apply { alphaCutoff = 0.2f },
            "transparent" to draw().apply { transparent = true },
            "format" to draw(format = VertexFormat.PositionNormalColor),
        )
        changed.forEach { (what, other) -> assertFalse(inputs.matches(other, FRAME, COUNT, false), what) }
        assertFalse(inputs.matches(draw(), FRAME, COUNT + 4, false), "the material's block size")
        assertFalse(inputs.matches(draw(), FRAME, COUNT, true), "alpha to coverage")
        assertFalse(inputs.matches(draw(), FRAME, COUNT, false, kind = 1), "another kind of draw in the slot")
        assertTrue(inputs.matches(draw(), FRAME, COUNT, false))
    }

    @Test
    fun anAnimatedDrawNeverMatchesAtALaterClock() {
        val inputs = DrawUniformInputs()
        val scrolling = pbr(animation = TextureAnimation(scrollU = 0.5f))
        inputs.record(draw(time = 1f, extras = scrolling), FRAME, COUNT, false)
        assertFalse(inputs.matches(draw(time = 2f, extras = scrolling), FRAME, COUNT, false), "a scrolling texture")

        inputs.record(draw(time = 1f, wave = WAVE), FRAME, COUNT, false)
        assertFalse(inputs.matches(draw(time = 2f, wave = WAVE), FRAME, COUNT, false), "a waving mesh")
    }

    @Test
    fun aClearedOrUntrackedSlotNeverMatches() {
        val inputs = DrawUniformInputs()
        assertFalse(inputs.matches(draw(), FRAME, COUNT, false), "nothing recorded")
        inputs.record(draw(), -2, COUNT, false)
        assertFalse(inputs.matches(draw(), -2, COUNT, false), "a frame nothing tracked")
        inputs.record(draw(), FRAME, COUNT, false)
        inputs.clear()
        assertFalse(inputs.matches(draw(), FRAME, COUNT, false))
    }

    private fun FrameUniformInputs.update(frame: Frame): Int = update(
        viewProjection = frame.viewProjection,
        cameraEye = frame.eye,
        cameraForward = Vec3f(0f, 0f, -1f),
        lightPayload = floatArrayOf(0.4f, 0.8f, 0.4f, 1f),
        shadowCascades = GpuShadowCascadeData(listOf(Mat4(), Mat4()), floatArrayOf(10f, frame.split)),
        fogColor = Color.Black,
        fogDensity = frame.fog,
        debugView = GpuDebugView.Off,
        exposure = 1f,
    )

    private class Frame(val viewProjection: Mat4, val eye: Vec3f, val split: Float, val fog: Float)

    private fun frameOf(eye: Vec3f = Vec3f(0f, 4f, 6f), split: Float = 30f, fog: Float = 0f) =
        Frame(Mat4().apply { data[12] = eye.x }, eye, split, fog)

    @Suppress("LongParameterList")
    private fun draw(
        time: Float = 1f,
        x: Float = 0f,
        extras: FloatArray = pbr(),
        wave: Vec3f = Vec3f.ZERO,
        format: VertexFormat = VertexFormat.PositionNormalColorUv,
    ) = RenderDrawCommand(
        mesh = FakeMesh(format),
        material = FakeMaterial,
        model = Mat4().apply { data[12] = x },
        extraUniformFloats = extras,
        vertexAnimation = wave,
        timeSeconds = time,
    )

    private fun pbr(roughness: Float = 1f, animation: TextureAnimation = TextureAnimation.None) =
        pbrMaterialFloats(0f, roughness, Color.White, Color.Transparent, animation)

    private class FakeMesh(override val format: VertexFormat) : Mesh {
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private object FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val FRAME = 3
        val COUNT = MaterialUniformLayouts.PbrTextured.total
        val WAVE = Vec3f(0.4f, 1f, 3f)
    }
}
