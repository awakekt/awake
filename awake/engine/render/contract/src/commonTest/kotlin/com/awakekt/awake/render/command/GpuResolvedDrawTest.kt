/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GpuResolvedDrawTest {
    @Test
    fun resolvedDrawOwnsOnlyOpaqueHardwareHandles() {
        val pipeline = object : PipelineHandle {}
        val material = object : MaterialBinding {}
        val draw = GpuResolvedDraw(
            pipeline = pipeline,
            materialBinding = material,
            vertexBuffer = null,
            indexBuffer = null,
            elementCount = 3,
            instances = 2,
        )

        assertEquals(pipeline, draw.pipeline)
        assertEquals(material, draw.materialBinding)
        assertEquals(2, draw.instances)
    }

    @Test
    fun resolvedDrawRejectsZeroInstances() {
        assertFailsWith<IllegalArgumentException> {
            GpuResolvedDraw(
                pipeline = object : PipelineHandle {},
                materialBinding = object : MaterialBinding {},
                vertexBuffer = null,
                indexBuffer = null,
                elementCount = 3,
                instances = 0,
            )
        }
    }
}
