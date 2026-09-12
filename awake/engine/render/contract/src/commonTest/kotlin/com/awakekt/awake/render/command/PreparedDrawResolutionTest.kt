/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import kotlin.test.Test
import kotlin.test.assertEquals

class PreparedDrawResolutionTest {
    @Test
    fun copiesEveryPreparedHandleAndOrderingKey() {
        val draw = object : PreparedDraw {
            override val pipeline = object : PipelineHandle {}
            override val materialBinding = object : MaterialBinding {}
            override val vertexBuffer = object : BufferHandle {}
            override val indexBuffer = null
            override val elementCount = 6
            override val transparent = true
            override val depthSortKey = 12f
            override val batchKey = 7
            override val instances = 3
            override val instanceVertexBuffer = object : BufferHandle {}
            override val jointPaletteBinding = object : MaterialBinding {}
            override val shadowBinding = object : MaterialBinding {}
            override val sceneDepthBinding = object : MaterialBinding {}
            override val instanceColorBuffer = object : BufferHandle {}
            override val instanceFrameBuffer = object : BufferHandle {}
        }

        val resolved = draw.toGpuResolvedDraw()

        assertEquals(draw.pipeline, resolved.pipeline)
        assertEquals(draw.materialBinding, resolved.materialBinding)
        assertEquals(draw.vertexBuffer, resolved.vertexBuffer)
        assertEquals(draw.elementCount, resolved.elementCount)
        assertEquals(draw.transparent, resolved.transparent)
        assertEquals(draw.depthSortKey, resolved.depthSortKey)
        assertEquals(draw.batchKey, resolved.batchKey)
        assertEquals(draw.instances, resolved.instances)
        assertEquals(draw.instanceVertexBuffer, resolved.instanceVertexBuffer)
        assertEquals(draw.jointPaletteBinding, resolved.jointPaletteBinding)
        assertEquals(draw.shadowBinding, resolved.shadowBinding)
        assertEquals(draw.sceneDepthBinding, resolved.sceneDepthBinding)
        assertEquals(draw.instanceColorBuffer, resolved.instanceColorBuffer)
        assertEquals(draw.instanceFrameBuffer, resolved.instanceFrameBuffer)
    }
}
