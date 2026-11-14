/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.material.GpuMaterial
import com.awakekt.awake.render.mesh.GpuMesh
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A request built without a vertex animation allocates no vector for it: `Vec3f.ZERO` builds a new
 * one per read, and the default read it for every draw a caller built (#1555's planning probe put
 * that at about 19% of a static scene's planning garbage).
 *
 * Measured on this host: 120 bytes a request before, 96 after; the 24 bytes were the vector.
 */
class GpuDrawRequestAllocationTest {
    private val mesh = object : GpuMesh {
        override val format = VertexFormat.PositionNormalColor
    }
    private val material = object : GpuMaterial {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
    private val model = Mat4()

    @Test
    fun requestsWithoutAnAnimationShareOneZeroVector() {
        val first = GpuDrawRequest(mesh, material, model)
        val second = GpuDrawRequest(mesh, material, model)
        assertSame(first.vertexAnimation, second.vertexAnimation)
        assertTrue(first.vertexAnimation.x == 0f && first.vertexAnimation.y == 0f && first.vertexAnimation.z == 0f)
        assertNotSame(first.vertexAnimation, GpuDrawRequest(mesh, material, model, vertexAnimation = Vec3f(1f, 0f, 0f)).vertexAnimation)
    }

    @Test
    fun buildingARequestAllocatesNoVector() {
        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        var sink: GpuDrawRequest? = null
        repeat(WARMUP) { sink = GpuDrawRequest(mesh, material, model) }
        val start = threads.currentThreadAllocatedBytes
        repeat(COUNT) { sink = GpuDrawRequest(mesh, material, model) }
        val perRequest = (threads.currentThreadAllocatedBytes - start) / COUNT
        println("PERF GpuDrawRequest bytes per request=$perRequest (${sink?.mesh != null})")
        assertTrue(perRequest <= MAX_BYTES_PER_REQUEST, "a request allocates $perRequest bytes")
    }

    private companion object {
        const val WARMUP = 20_000
        const val COUNT = 100_000

        // The request alone; the 24-byte vector it used to build would push it past this.
        const val MAX_BYTES_PER_REQUEST = 104L
    }
}
