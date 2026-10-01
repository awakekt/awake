/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class PbrMaterialPackingTest {
    @Test
    fun anUnchangedMaterialIsPackedOnceAndAChangedOneAgain() {
        val material = PbrMaterial(roughness = 1f)
        val first = material.packedFloats()
        assertSame(first, material.packedFloats(), "nothing changed, so the same floats")

        material.baseColorFactor = Color(1f, 0f, 0f)
        val second = material.packedFloats()

        assertNotSame(first, second)
        assertContentEquals(pbrMaterialFloats(0f, 1f, Color(1f, 0f, 0f), Color.Transparent), second)
    }
}
