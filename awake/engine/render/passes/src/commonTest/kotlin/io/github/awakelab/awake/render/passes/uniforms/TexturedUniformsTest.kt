/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.passes.uniforms

import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.render.renderer.MAX_POINT_LIGHTS
import io.github.awakelab.awake.render.renderer.SceneLight
import io.github.awakelab.awake.render.renderer.UniformFields
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the `textured.wgsl` uniform block's shape and field placement.
 *
 * Both backends assembled this independently until it moved into [texturedUniforms]; nothing
 * ran over the result. No renderer test reaches it either -- the textured path needs a
 * `PositionNormalColorUv` mesh, and every headless fixture uses `PositionColorUv`. A field
 * written to the wrong offset here produces a correctly-sized buffer of wrong numbers, which
 * renders without erroring, so the offsets are asserted directly.
 */
class TexturedUniformsTest {

    private val light = sceneLightUniforms(
        SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f)),
        eye = Vec3f.ZERO,
    )

    private class FakeMesh : io.github.awakelab.awake.render.mesh.Mesh {
        override val format = io.github.awakelab.awake.core.geometry.VertexFormat.PositionNormalColorUv
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private class FakeMaterial : io.github.awakelab.awake.render.material.Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private fun block(model: Mat4 = Mat4(), eye: Vec3f = Vec3f.ZERO) = texturedUniforms(
        drawCall = io.github.awakelab.awake.render.renderer.DrawCall(
            mesh = FakeMesh(),
            material = FakeMaterial(),
            model = model,
        ),
        mvp = Mat4(),
        frame = SceneFrameUniforms(light, eye, floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)),
    )

    @Test
    fun blockIsExactlyTheDeclaredLayoutSize() {
        assertEquals(MaterialUniformLayouts.PbrTextured.total, block().size)
    }

    @Test
    fun eachFieldLandsAtItsDeclaredOffset() {
        val eye = Vec3f(3f, 5f, 7f)
        val floats = block(eye = eye)
        val layout = MaterialUniformLayouts.PbrTextured

        val cameraAt = layout.offsetOf(UniformFields.CameraPosition)
        assertEquals(
            listOf(3f, 5f, 7f, 0f),
            floats.slice(cameraAt until cameraAt + UniformFields.CameraPosition.floats),
            "camera position should sit at its declared offset",
        )

        val fogAt = layout.offsetOf(UniformFields.FogColor)
        assertEquals(
            listOf(0.1f, 0.2f, 0.3f, 0.4f),
            floats.slice(fogAt until fogAt + UniformFields.FogColor.floats),
            "fog should sit at its declared offset -- it is written last and is the field most " +
                "likely to be silently truncated if the layout and the writer disagree",
        )
    }

    @Test
    fun pointLightSlotsAreReservedEvenWithNoPointLights() {
        val at = MaterialUniformLayouts.PbrTextured.offsetOf(UniformFields.PointLightPositions)
        val slots = block().slice(at until at + UniformFields.PointLightPositions.floats)
        assertEquals(MAX_POINT_LIGHTS * VEC4, slots.size)
        assertEquals(List(slots.size) { 0f }, slots, "unused point-light slots must read zero")
    }

    private companion object {
        const val VEC4 = 4
    }
}
