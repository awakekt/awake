/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.material.GpuMaterial
import com.awakekt.awake.render.mesh.GpuMesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SolidOpaqueDrawSelectionTest {
    private object StandardPlain
    private object SolidPlain
    private object StandardBack
    private object SolidBack
    private object StandardSkinned
    private object SolidSkinned

    private class FakeGpuMesh(override val format: VertexFormat) : GpuMesh

    private class FakeGpuMaterial : GpuMaterial {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private fun command(
        alphaMode: AlphaMode = AlphaMode.Opaque,
        transparent: Boolean = false,
        fadeOpacity: Float = 1f,
        instanceColors: List<Vec4>? = null,
    ) = RenderDrawCommand(
        mesh = FakeGpuMesh(VertexFormat.PositionNormalColorUv),
        material = FakeGpuMaterial(),
        transparent = transparent,
        alphaMode = alphaMode,
        instanceColors = instanceColors,
    ).apply {
        this.fadeOpacity = fadeOpacity
    }

    @Test
    fun usesSolidOpaquePipelineOnlyWhenFragmentShaderCannotDiscard() {
        assertTrue(command().usesSolidOpaquePipeline())
        assertTrue(
            command(instanceColors = listOf(Vec4(1f, 0.5f, 0.25f, 1f), Vec4(0.8f, 0.8f, 0.8f, 1f))).usesSolidOpaquePipeline(),
            "per-instance RGB tint with full alpha still qualifies for Early-Z solid pipeline",
        )
        assertFalse(command(transparent = true).usesSolidOpaquePipeline())
        assertFalse(command(alphaMode = AlphaMode.Masked).usesSolidOpaquePipeline())
        assertFalse(command(fadeOpacity = 0.5f).usesSolidOpaquePipeline())
        assertFalse(
            command(instanceColors = listOf(Vec4(1f, 1f, 1f, 1f), Vec4(1f, 1f, 1f, 0.99f))).usesSolidOpaquePipeline(),
            "any faded instance must fall back to the dither-discard pipeline",
        )
    }

    @Test
    fun resolveInstancedPrefersSolidCompanionWhenSolidOpaqueIsTrue() {
        val format = VertexFormat.PositionNormalColorUv
        val skinFormat = VertexFormat.PositionNormalColorSkin
        val table = PipelineTable(
            primary = StandardPlain,
            primaryFormat = format,
            instancedByFormat = mapOf(format to StandardPlain),
            instancedBackCulledByFormat = mapOf(format to StandardBack),
            skinnedInstancedByFormat = mapOf(skinFormat to StandardSkinned),
            solidInstancedByFormat = mapOf(format to SolidPlain),
            solidInstancedBackCulledByFormat = mapOf(format to SolidBack),
            solidSkinnedInstancedByFormat = mapOf(skinFormat to SolidSkinned),
        )

        assertEquals(SolidPlain, table.resolveInstanced(format, InstancedDrawKind.Plain, solidOpaque = true))
        assertEquals(StandardPlain, table.resolveInstanced(format, InstancedDrawKind.Plain, solidOpaque = false))
        assertEquals(SolidBack, table.resolveInstanced(format, InstancedDrawKind.Plain, cullMode = CullMode.Back, solidOpaque = true))
        assertEquals(StandardBack, table.resolveInstanced(format, InstancedDrawKind.Plain, cullMode = CullMode.Back, solidOpaque = false))
        assertEquals(SolidSkinned, table.resolveInstanced(skinFormat, InstancedDrawKind.Skinned, solidOpaque = true))
        assertEquals(StandardSkinned, table.resolveInstanced(skinFormat, InstancedDrawKind.Skinned, solidOpaque = false))
    }
}
