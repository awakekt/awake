/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SolidOpaquePipelineResolutionTest {
    private fun spec(path: String, multisample: MultisampleState = MultisampleState.Single) = PipelineSpec(
        vertexFormat = VertexFormat.PositionNormalColorUv,
        vertexShader = ShaderSource.ResourcePath(path, "vertexMain"),
        fragmentShader = ShaderSource.ResourcePath(path, "fragmentMain"),
        variant = PipelineVariant.Opaque,
        multisample = multisample,
    )

    @Test
    fun solidOpaqueCompanionsDisableAlphaToCoverageAndResolveByCullMode() = runTest {
        val format = VertexFormat.PositionNormalColorUv
        val msaaA2c = MultisampleState(count = 4, alphaToCoverageEnabled = true)
        val request = PipelineRequest(
            key = PipelineKey.Primary,
            spec = spec("shaders/standard.wgsl", msaaA2c),
            buildBackCulled = true,
            buildTransparent = true,
            solidSpec = spec("shaders/solid.wgsl", msaaA2c),
        )
        val set = buildPipelineTable(listOf(request)) { _, s -> s }.getValue(PipelineKey.Primary)

        val solidFill = assertNotNull(set.solidFill)
        val solidBack = assertNotNull(set.solidBackCulled)
        assertFalse(solidFill.multisample.alphaToCoverageEnabled, "solidFill must disable alpha-to-coverage for Early-Z")
        assertFalse(solidBack.multisample.alphaToCoverageEnabled, "solidBackCulled must disable alpha-to-coverage for Early-Z")
        assertEquals(4, solidFill.multisample.count)
        assertEquals(CullMode.None, solidFill.cullMode)
        assertEquals(CullMode.Back, solidBack.cullMode)
        assertTrue(solidFill in set.all && solidBack in set.all)

        val table = PipelineTable(
            primary = set.fill,
            primaryFormat = format,
            backCulledByFormat = mapOf(format to requireNotNull(set.backCulled)),
            transparentByFormat = mapOf(format to requireNotNull(set.transparent)),
            solidByFormat = mapOf(format to solidFill),
            solidBackCulledByFormat = mapOf(format to solidBack),
        )

        assertEquals(solidFill, table.resolve(format, CullMode.None, depthWrite = true, solidOpaque = true))
        assertEquals(solidBack, table.resolve(format, CullMode.Back, depthWrite = true, solidOpaque = true))
        assertEquals(set.fill, table.resolve(format, CullMode.None, depthWrite = true, solidOpaque = false))
        assertEquals(set.backCulled, table.resolve(format, CullMode.Back, depthWrite = true, solidOpaque = false))
        assertEquals(set.transparent, table.resolve(format, CullMode.None, transparent = true, depthWrite = false, solidOpaque = true))
    }

    @Test
    fun skyVariantsTestDepthLessEqualWithoutDepthWrite() {
        for (variant in listOf(PipelineVariant.Sky, PipelineVariant.SkyBlended, PipelineVariant.SkyAdditive)) {
            assertTrue(variant.depthTestEnabled, "$variant must test depth for Early-Z sky rejection")
            assertFalse(variant.depthWriteEnabled, "$variant must not overwrite scene depth")
            assertTrue(variant.depthCompareLessEqual, "$variant must compare LESS_EQUAL so far-plane z=1 passes cleared depth")
        }
        assertFalse(PipelineVariant.Sky.blendEnabled)
        assertTrue(PipelineVariant.SkyBlended.blendEnabled)
        assertFalse(PipelineVariant.SkyBlended.additive)
        assertTrue(PipelineVariant.SkyAdditive.blendEnabled)
        assertTrue(PipelineVariant.SkyAdditive.additive)
    }
}
