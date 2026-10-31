/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshoptimizer

import com.awakekt.awake.asset.gltf.GltfParser
import com.awakekt.awake.asset.gltf.parseMeshPrimitives
import java.nio.file.Files
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LodCookTest {
    @Test
    fun cooksEachAssetWithTheProjectSettingsAndHonoursAnOptOut() {
        val project = Files.createTempDirectory("lod-project").toFile()
        val output = Files.createTempDirectory("lod-output").toFile()
        try {
            val (positions, indices) = sphere(segments = 32, rings = 16)
            project.resolve("props").mkdirs()
            listOf("props/rock.gltf", "props/fence.gltf").forEach {
                project.resolve(it).writeText(GltfWriter.writePositionOnlyMesh(positions, indices))
            }
            project.resolve("awake.lod.json").writeText(
                """{"defaults":{"levels":[{"ratio":0.5,"screenCoverage":0.1},{"ratio":0.2,"screenCoverage":0}]},""" +
                    """"assets":{"props/fence.gltf":{"enabled":false}}}""",
            )

            val lines = LodCook.run(project, listOf("props/rock.gltf", "props/fence.gltf"), output)

            assertTrue(lines[0].startsWith("props/rock.gltf: ${indices.size / 3} -> "), lines[0])
            assertEquals("props/fence.gltf: opted out, copied unchanged", lines[1])
            val rock = GltfParser.parseMeshPrimitives(output.resolve("props/rock.gltf").readBytes())
            assertEquals(3, rock.size, "the full mesh and two levels")
            assertTrue(rock[2].single().indices.size < rock[1].single().indices.size)
            assertEquals(1, GltfParser.parseMeshPrimitives(output.resolve("props/fence.gltf").readBytes()).size)
        } finally {
            project.deleteRecursively()
            output.deleteRecursively()
        }
    }

    /** A closed sphere with one vertex per position. */
    private fun sphere(segments: Int, rings: Int): Pair<FloatArray, IntArray> {
        val positions = mutableListOf(0f, 1f, 0f)
        for (ring in 1 until rings) {
            val theta = Math.PI * ring / rings
            for (segment in 0 until segments) {
                val phi = 2 * Math.PI * segment / segments
                positions += listOf((sin(theta) * cos(phi)).toFloat(), cos(theta).toFloat(), (sin(theta) * sin(phi)).toFloat())
            }
        }
        positions += listOf(0f, -1f, 0f)
        val bottom = positions.size / 3 - 1
        fun at(ring: Int, segment: Int) = 1 + (ring - 1) * segments + segment % segments
        val indices = mutableListOf<Int>()
        for (segment in 0 until segments) {
            indices += listOf(0, at(1, segment + 1), at(1, segment))
            indices += listOf(bottom, at(rings - 1, segment), at(rings - 1, segment + 1))
            for (ring in 1 until rings - 1) {
                indices += listOf(at(ring, segment), at(ring, segment + 1), at(ring + 1, segment))
                indices += listOf(at(ring, segment + 1), at(ring + 1, segment + 1), at(ring + 1, segment))
            }
        }
        return positions.toFloatArray() to indices.toIntArray()
    }
}
