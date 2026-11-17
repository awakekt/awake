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
import com.awakekt.awake.render.passes.uniforms.DrawBlockWriters
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.ParticleDrawLayout
import com.awakekt.awake.render.passes.uniforms.drawBlockFloats
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.renderer.SkinnedDrawLayout
import com.awakekt.awake.render.renderer.SkinnedMaterialLayout
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A moving draw's own block, packed with a renderer's [DrawBlockWriters], refills the writers'
 * arrays instead of building a writer, its array and a material payload per draw per frame: about
 * 116 KB a frame in a populated scene a consumer measured. The floats are the same either way.
 */
class DrawBlockWritersAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun packingWithWritersGivesTheSameFloats() {
        val writers = DrawBlockWriters()
        for (case in cases) {
            for (draw in case.draws) {
                val fresh = draw.drawBlockFloats(case.floats, alphaToCoverageEnabled = true, instancedKind = case.kind)
                val kept = draw.drawBlockFloats(case.floats, alphaToCoverageEnabled = true, instancedKind = case.kind, writers = writers)
                assertEquals(fresh.toList(), kept.toList(), "${case.name}: the writers pack the same block")
            }
        }
    }

    @Test
    fun packingWithWritersDropsThePerDrawArrays() {
        val diagnostics = ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean::class.java)
        assertEquals("false", diagnostics.getVMOption("DoEscapeAnalysis").value, "this gate needs -XX:-DoEscapeAnalysis")
        val writers = DrawBlockWriters()
        for (case in cases) {
            val fresh = bytesPerPack(case.draws) { draw -> draw.drawBlockFloats(case.floats, true, case.kind).size }
            val kept = bytesPerPack(case.draws) { draw -> draw.drawBlockFloats(case.floats, true, case.kind, writers).size }
            println("${case.name} draw block: $fresh B a pack without writers, $kept B with")
            // At the least the block's own float array is gone; the writer and any payload copy with it.
            assertTrue(kept + floatArrayBytes(case.floats) <= fresh, "${case.name}: $kept B with writers against $fresh B without")
        }
    }

    private inline fun bytesPerPack(draws: List<RenderDrawCommand>, pack: (RenderDrawCommand) -> Int): Long {
        var sink = 0
        repeat(WARM_UP) { sink += pack(draws[it % draws.size]) }
        val before = threads.currentThreadAllocatedBytes
        repeat(PACKS) { sink += pack(draws[it % draws.size]) }
        val bytes = threads.currentThreadAllocatedBytes - before
        assertTrue(sink > 0)
        return bytes / PACKS
    }

    /** A float array's header and its floats, rounded to the JVM's 8-byte alignment. */
    private fun floatArrayBytes(floats: Int): Long = (ARRAY_HEADER + floats * Float.SIZE_BYTES + 7L) / 8 * 8

    private class Case(val name: String, val floats: Int, val kind: InstancedDrawKind?, val draws: List<RenderDrawCommand> = litDraws)

    private companion object {
        const val PACKS = 20_000
        const val WARM_UP = 2_000
        const val ARRAY_HEADER = 16L

        fun mesh(vertexFormat: VertexFormat) = object : Mesh {
            override val format = vertexFormat
            override val sizeBytes = 0L
            override fun destroy() = Unit
        }

        val material = object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        }

        /** Draws in different places with a full payload, built once so packing them allocates nothing of the test's. */
        val litDraws = draws(VertexFormat.PositionNormalColor, MaterialUniformLayouts.PbrTexturedMaterial.total)

        /** Skinned draws carrying a joint palette and their factors. */
        val skinnedDraws = draws(VertexFormat.PositionNormalColorSkin, SkinnedMaterialLayout.total)

        fun draws(format: VertexFormat, payloadFloats: Int): List<RenderDrawCommand> {
            val mesh = mesh(format)
            return List(8) { index ->
                RenderDrawCommand(
                    mesh,
                    material,
                    model = Mat4().translate(index.toFloat(), 0f, -index.toFloat()),
                    extraUniformFloats = FloatArray(payloadFloats) { 0.01f * (it + index) },
                )
            }
        }

        val cases = listOf(
            Case("textured", MaterialUniformLayouts.PbrTexturedDraw.total, null),
            Case("lit", MaterialUniformLayouts.LitDraw.total, null),
            Case("lit shadow", MaterialUniformLayouts.LitShadowDraw.total, null),
            Case("particle", ParticleDrawLayout.total, InstancedDrawKind.Particle),
            // The path a populated scene's measurement led through: a skinned draw's own block.
            Case("skinned", SkinnedDrawLayout.total, null, skinnedDraws),
        )
    }
}
