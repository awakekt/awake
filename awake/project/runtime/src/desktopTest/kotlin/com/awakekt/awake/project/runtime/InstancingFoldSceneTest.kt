/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.render.passes.InstanceFoldMiss
import com.awakekt.awake.render.passes.SceneDrawStats
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.rendering.debug.RenderDiagnostics
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A town of repeated glTF models played through the project runtime's model path, as a consumer
 * game's is: [COPIES] each of a textured single-sided prop, alpha-masked double-sided foliage, an
 * untextured double-sided prop and an animated skinned character, under a shadow-casting sun.
 *
 * What it asserts is what the scene compiler hands each pass, counted by [SceneDrawStats]: the
 * evidence for which draws fold into instances and why the others don't.
 */
class InstancingFoldSceneTest {
    @AfterTest
    fun turnStatsOff() {
        RenderDiagnostics.drawStatsEnabled = false
    }

    @Test
    fun repeatedTownModelsAndWhyTheyDoNotFold() = runTest {
        val stats = playTown()
        println("Town scene, $COPIES copies of 4 models: $stats")

        assertEquals(COPIES * MODELS, stats.sourceDraws)
        // Back-culled props reach the planner's preparer, which answered for unculled copies only.
        assertEquals(COPIES, stats.misses(InstanceFoldMiss.CullMode), "back-culled houses: $stats")
        assertEquals(COPIES, stats.misses(InstanceFoldMiss.SkinnedNotInstanced), "posed villagers: $stats")
        assertEquals(2, stats.foldedDraws, "bushes and fences fold: $stats")
        assertEquals(2 + 2 * COPIES, stats.mainPassDraws)
        // Each cascade draws the same folded casters: nothing folds differently in the shadow passes.
        assertEquals(List(stats.shadowPasses) { stats.opaqueDraws }, List(stats.shadowPasses) { stats.shadowPassDraws(it) })
    }

    @Test
    fun drawStatsCostNothingWhileOff() = runTest {
        RenderDiagnostics.drawStatsEnabled = false
        val project = loadProject(townFiles())
        val game = app { scene("play") { runProject(project) } }
        game.ready(PlanInstancingRenderer(ProjectRenderPlan))
        game.update(FRAME, WIDTH, HEIGHT)

        assertEquals(null, RenderDiagnostics.drawStats, "nothing is counted, or kept, while the stats are off")
    }

    private suspend fun playTown(): SceneDrawStats {
        val project = loadProject(townFiles())
        val game = app { scene("play") { runProject(project) } }
        game.ready(PlanInstancingRenderer(ProjectRenderPlan))
        RenderDiagnostics.drawStatsEnabled = true
        repeat(FRAMES) { game.update(FRAME, WIDTH, HEIGHT) }
        return checkNotNull(RenderDiagnostics.drawStats) { "the scene was not planned with stats on" }
    }

    private fun townFiles() = files(
        "awake.project.json" to MANIFEST,
        "scenes/main.scene.json" to townScene(),
        HOUSE to triangleModel(ModelKind(textured = true)),
        BUSH to triangleModel(ModelKind(textured = true, masked = true)),
        FENCE to triangleModel(ModelKind(textured = false)),
        VILLAGER to triangleModel(ModelKind(textured = false, skinned = true)),
    )

    /** A camera over four rows of [COPIES] models and a sun that casts. */
    private fun townScene(): String {
        val rows = listOf(
            Row(HOUSE, "gltf-material:$HOUSE", cullMode = "Back"),
            Row(BUSH, "gltf-material:$BUSH", cullMode = "None"),
            Row(FENCE, "lit-shadow", cullMode = "None"),
            Row(VILLAGER, "skinned-material", cullMode = "None"),
        )
        val models = rows.flatMapIndexed { row, model ->
            List(COPIES) { copy ->
                val x = (copy - COPIES / 2) * SPACING
                val z = (row - 2) * SPACING
                """{ "name": "${model.path.substringAfterLast('/')}-$copy", "transform": { "position": { "x": $x, "y": 0.0, "z": $z } },
                     "components": [ { "component": "mesh_renderer", "mesh": "${model.path}", "material": "${model.material}", "cull_mode": "${model.cullMode}" } ] }"""
            }
        }
        return """
        { "version": 1, "name": "town", "nodes": [
          { "name": "Camera", "components": [ { "component": "camera", "eye": { "x": 0.0, "y": 8.0, "z": 16.0 },
            "center": { "x": 0.0, "y": 0.0, "z": 0.0 }, "fovYDegrees": 60.0, "near": 0.1, "far": 200.0 } ] },
          { "name": "Sun", "components": [ { "component": "light", "type": "Directional",
            "direction": { "x": 0.55, "y": 1.0, "z": 0.35 }, "shadowDistance": 60.0 } ] },
          ${models.joinToString(",\n")}
        ] }
        """.trimIndent()
    }

    private data class Row(val path: String, val material: String, val cullMode: String)

    private companion object {
        const val COPIES = 8
        const val MODELS = 4
        const val SPACING = 1.5
        const val FRAMES = 3
        const val HOUSE = "models/house.gltf"
        const val BUSH = "models/bush.gltf"
        const val FENCE = "models/fence.gltf"
        const val VILLAGER = "models/villager.gltf"
        const val MANIFEST =
            """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
    }
}
