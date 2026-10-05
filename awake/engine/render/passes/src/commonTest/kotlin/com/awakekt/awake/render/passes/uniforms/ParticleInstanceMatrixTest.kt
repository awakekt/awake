/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.math.Mat4
import kotlin.test.Test
import kotlin.test.assertEquals

class ParticleInstanceMatrixTest {
    @Test
    fun aParticleInstanceCarriesItsCentreSizeStretchAndRotationWhereTheShaderReadsThem() {
        val model = Mat4().setParticleInstance(1f, 2f, 3f, size = 4f, stretchX = 5f, stretchY = 6f, stretchZ = 7f, rotation = 0.5f)

        assertEquals(listOf(1f, 2f, 3f), listOf(model.m03, model.m13, model.m23), "column 3 is the world centre")
        assertEquals(4f, model.m00, "column 0's length is the size")
        assertEquals(listOf(5f, 6f, 7f), listOf(model.m01, model.m11, model.m21), "column 1 is the stretch vector")
        assertEquals(0.5f, model.m02, "column 2's x is the rotation in radians")
    }

    @Test
    fun aParticleWithNoRotationWritesZeroSoAnUnspunSpriteStaysUpright() {
        val model = Mat4().setParticleInstance(0f, 0f, 0f, size = 1f)

        assertEquals(0f, model.m02)
    }

    @Test
    fun reusingAMatrixForAnotherParticleOverwritesTheOldRotation() {
        val model = Mat4().setParticleInstance(0f, 0f, 0f, size = 1f, rotation = 2f)

        model.setParticleInstance(0f, 0f, 0f, size = 1f)

        assertEquals(0f, model.m02, "the pooled matrix must not keep the last particle's turn")
    }
}
