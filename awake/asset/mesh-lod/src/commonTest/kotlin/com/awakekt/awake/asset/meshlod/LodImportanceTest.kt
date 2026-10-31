/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

import com.awakekt.awake.asset.gltf.GltfParser
import com.awakekt.awake.asset.gltf.parseMeshPrimitives
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Detail kept where a game wants it: per-joint importance, per-mesh ratios and per-mesh opt-out. */
class LodImportanceTest {
    // Joint 0 ("Body") weights the lower half, joint 1 ("Head") the upper half.
    private val tube = TestMeshes.skinnedTube(segments = 24, rings = 32)

    /** Kept vertices in the upper (head) half and the lower (body) half. */
    private fun keptHalves(indices: IntArray): Pair<Int, Int> {
        val kept = indices.toSet()
        val head = kept.count { tube.positions[it * 3 + 1] > 2f }
        val body = kept.count { tube.positions[it * 3 + 1] < 2f }
        return head to body
    }

    @Test
    fun verticesWeightedToAnImportantJointAreReducedLessThanTheBody() {
        val importance = FloatArray(tube.vertexCount) { tube.weights!![it * 4 + 1] * 8f }
        val options = SimplifyOptions(targetRatio = 0.25f)
        val skin = SkinInfluences(tube.joints!!, tube.weights!!)
        val plain = AttributeMeshSimplifier.simplify(tube.positions, tube.indices, options, skin = skin)
        val weighted = AttributeMeshSimplifier.simplify(tube.positions, tube.indices, options, skin = skin, importance = importance)

        assertEquals(plain.triangleCount, weighted.triangleCount, "importance moves detail; it doesn't change the target")
        val (head, body) = keptHalves(weighted.indices)
        val (plainHead, _) = keptHalves(plain.indices)
        assertTrue(head > body * 2, "head kept $head vertices, body $body")
        assertTrue(head > plainHead, "head kept $head vertices with importance, $plainHead without")
    }

    @Test
    fun theCookerWeighsJointsByName() {
        val settings = MeshLodSettings(levels = listOf(LodLevelSetting(0.25f, 0f)), jointImportance = mapOf("Head" to 8f))
        val cooked = GltfLodCooker.cook(TestGlb.write(tube), settings)
        val level = GltfParser.parseMeshPrimitives(cooked.bytes)[1].single()
        val (head, body) = keptHalves(level.indices)
        // The cooker locks borders, so each half also keeps its 24 open-end vertices.
        assertTrue(head - 24 > (body - 24) * 1.5, "head kept $head vertices, body $body")
    }

    @Test
    fun aMeshCanKeepMoreDetailOrOptOut() {
        val sphere = TestMeshes.uvSphere(segments = 48, rings = 24)
        val glb = TestGlb.write(sphere, nodeName = "Head")
        val detailed = listOf(LodLevelSetting(0.7f, 0.1f), LodLevelSetting(0.5f, 0.05f), LodLevelSetting(0.3f, 0f))
        val settings = MeshLodSettings(meshes = mapOf("Head" to MeshLodOverride(levels = detailed)))

        val triangles = GltfLodCooker.cook(glb, settings).meshes.single().triangles
        listOf(0.7f, 0.5f, 0.3f).forEachIndexed { i, ratio ->
            assertTrue(kotlin.math.abs(triangles[i + 1] - triangles[0] * ratio) <= triangles[0] * 0.02f, "level ${i + 1}: $triangles")
        }
        val optedOut = MeshLodSettings(meshes = mapOf("Head" to MeshLodOverride(enabled = false)))
        assertEquals(LodCookOutcome.NothingToSimplify, GltfLodCooker.cook(glb, optedOut).outcome)
        val otherMeshOptedOut = MeshLodSettings(meshes = mapOf("Body" to MeshLodOverride(enabled = false)))
        assertEquals(LodCookOutcome.Generated, GltfLodCooker.cook(glb, otherMeshOptedOut).outcome)
    }

    @Test
    fun perMeshAndJointSettingsReadFromTheProjectFile() {
        val project = ProjectLodSettings.decode(
            """{"defaults":{"jointImportance":{"Head":4}},"assets":{"actors/hero.glb":{"meshes":{"Head":{"enabled":false}}}}}""",
        )
        val hero = project.forAsset("actors/hero.glb")
        assertEquals(mapOf("Head" to 4f), hero.jointImportance)
        assertEquals(false, hero.forMesh("Head").enabled)
        assertEquals(true, hero.forMesh("Body").enabled)
        assertEquals(project, ProjectLodSettings.decode(project.encode()))
    }
}
