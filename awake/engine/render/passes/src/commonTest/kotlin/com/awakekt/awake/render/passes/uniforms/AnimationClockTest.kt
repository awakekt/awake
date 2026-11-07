/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.instancedUniformFloats
import com.awakekt.awake.render.passes.uniformFloats
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

/**
 * A draw nothing animates packs the same block whatever the clock says, so a static prop's block
 * matches the frame before and need not be uploaded again (#1452); a draw that animates still
 * carries the clock its shader moves it by.
 *
 * Negative control, measured: writing the clock into every block, as before, fails each "still"
 * case here at its time slot (`VertexAnimation.w` or `TextureScroll.z`).
 */
class AnimationClockTest {
    @Test
    fun aStillLitDrawPacksTheSameBlockAtAnyTime() {
        assertContentEquals(lit(STILL, 1f), lit(STILL, 2f))
    }

    @Test
    fun aWavingLitDrawCarriesTheClock() {
        assertFalse(lit(WAVE, 1f).contentEquals(lit(WAVE, 2f)))
    }

    @Test
    fun aStillTexturedDrawPacksTheSameBlockAtAnyTime() {
        assertContentEquals(textured(TextureAnimation.None, 1f), textured(TextureAnimation.None, 2f))
        // A held frame of a sheet picks its cell without the clock.
        val held = TextureAnimation(columns = 2, rows = 2, frameCount = 1, framesPerSecond = 0f, firstFrame = 1)
        assertContentEquals(textured(held, 1f), textured(held, 2f))
    }

    @Test
    fun aScrollingOrPlayingTexturedDrawCarriesTheClock() {
        val playing = TextureAnimation(columns = 2, rows = 2, frameCount = 4, framesPerSecond = 2f)
        assertFalse(textured(playing, 1f).contentEquals(textured(playing, 2f)))
        val scrolling = TextureAnimation(scrollU = 0.5f)
        assertFalse(textured(scrolling, 1f).contentEquals(textured(scrolling, 2f)))
    }

    @Test
    fun aStillInstancedDrawPacksTheSameBlockAtAnyTime() {
        assertContentEquals(instanced(STILL, 1f), instanced(STILL, 2f))
        assertFalse(instanced(WAVE, 1f).contentEquals(instanced(WAVE, 2f)))
    }

    private fun lit(wave: Vec3f, time: Float): FloatArray =
        draw(VertexFormat.PositionNormalColor, wave, time, FloatArray(0)).uniformFloats(
            materialUniformFloatCount = MaterialUniformLayouts.LitShadow.total,
            viewProjection = Mat4(),
            cameraEye = EYE,
            lightPayload = LIGHT,
            shadowCascades = CASCADES,
            cameraForward = FORWARD,
        )

    private fun textured(animation: TextureAnimation, time: Float): FloatArray {
        val extras = pbrMaterialFloats(0f, 1f, Color.White, Color.Transparent, animation)
        return draw(VertexFormat.PositionNormalColorUv, STILL, time, extras).uniformFloats(
            materialUniformFloatCount = MaterialUniformLayouts.PbrTextured.total,
            viewProjection = Mat4(),
            cameraEye = EYE,
            lightPayload = LIGHT,
            shadowCascades = CASCADES,
            cameraForward = FORWARD,
        )
    }

    private fun instanced(wave: Vec3f, time: Float): FloatArray =
        draw(VertexFormat.PositionNormalColor, wave, time, FloatArray(0)).instancedUniformFloats(
            kind = InstancedDrawKind.Plain,
            viewProjection = Mat4(),
            lightPayload = LIGHT,
            cameraEye = EYE,
            shadowCascades = CASCADES,
            cameraForward = FORWARD,
        )

    private fun draw(format: VertexFormat, wave: Vec3f, time: Float, extras: FloatArray) = RenderDrawCommand(
        mesh = FakeMesh(format),
        material = FakeMaterial,
        extraUniformFloats = extras,
        vertexAnimation = wave,
        timeSeconds = time,
    )

    private class FakeMesh(override val format: VertexFormat) : Mesh {
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private object FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        val STILL = Vec3f(0f, 0f, 0f)
        val WAVE = Vec3f(0.4f, 1f, 3f)
        val EYE = Vec3f(0f, 4f, 6f)
        val FORWARD = Vec3f(0f, 0f, -1f)
        val LIGHT = sceneLightUniforms(DEFAULT_SCENE_LIGHT, EYE).packed
        val CASCADES = GpuShadowCascadeData(listOf(Mat4()), floatArrayOf(10f))
    }
}
