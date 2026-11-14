/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Asking whether a draw's uniforms are unchanged, which the WebGPU preparer does for every draw of
 * every planned frame, allocates nothing with escape analysis off (#1555). Kotlin/Wasm has none: a
 * range and its iterator per comparison were garbage there, three comparisons a draw.
 */
class DrawUniformInputsAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun checkingADrawAgainstItsLastBlockAllocatesNothing() {
        val diagnostics = ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean::class.java)
        assertEquals("false", diagnostics.getVMOption("DoEscapeAnalysis").value, "this gate needs -XX:-DoEscapeAnalysis")
        val draw = RenderDrawCommand(mesh, material, model = Mat4().translate(1f, 2f, 3f), extraUniformFloats = FloatArray(EXTRAS) { it.toFloat() })
        val inputs = DrawUniformInputs()
        inputs.record(draw, FRAME, EXTRAS, alphaToCoverage = false)
        var same = 0
        repeat(WARM_UP) { if (inputs.matches(draw, FRAME, EXTRAS, alphaToCoverage = false)) same++ }

        val thread = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(thread)
        repeat(CALLS) { if (inputs.matches(draw, FRAME, EXTRAS, alphaToCoverage = false)) same++ }
        val bytes = threads.getThreadAllocatedBytes(thread) - before

        assertEquals(WARM_UP + CALLS, same, "an unchanged draw matches its block")
        // The measurement's own few hundred bytes aside: nothing per draw.
        assertTrue(bytes < CALLS, "$CALLS checks allocated $bytes B")
    }

    private companion object {
        const val CALLS = 10_000
        const val WARM_UP = 1_000
        const val FRAME = 1
        const val EXTRAS = 24

        val mesh = object : Mesh {
            override val format = VertexFormat.PositionNormalColor
            override val sizeBytes = 0L
            override fun destroy() = Unit
        }

        val material = object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        }
    }
}
