/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.meshlod.GltfLodCooker
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.render.passes.SceneDrawStats
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.rendering.debug.RenderDiagnostics
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Copies of a model cooked with levels of detail, in rows at four distances, played through the
 * project runtime's model path with a renderer that instances as a backend built from the project's
 * render plan does. Copies drawing the same level share its mesh and their material, so each level
 * folds into one instanced draw per pass, counted by [SceneDrawStats].
 */
class LodFoldSceneTest {
    @AfterTest
    fun turnStatsOff() {
        RenderDiagnostics.drawStatsEnabled = false
    }

    @Test
    fun copiesAtEachLevelFoldIntoOneInstancedDraw() = runTest {
        val stats = playRows()
        println("LOD rows, $COPIES copies at ${DISTANCES.size} distances: $stats")

        assertEquals(COPIES * DISTANCES.size, stats.sourceDraws)
        assertEquals(DISTANCES.size, stats.foldedDraws, "one instanced draw per level: $stats")
        assertEquals(COPIES * DISTANCES.size, stats.foldedCopies)
        assertEquals(0, stats.totalMisses, "no copy stays on its own: $stats")
        assertEquals(DISTANCES.size, stats.mainPassDraws)
    }

    private suspend fun playRows(): SceneDrawStats {
        val cooked = GltfLodCooker.cook(sphereGltf().encodeToByteArray())
        val project = loadProject(files(MANIFEST_FILE to MANIFEST, SCENE_FILE to scene(), ROCK to cooked.bytes.decodeToString()))
        val game = app { scene("play") { runProject(project) } }
        game.ready(PlanInstancingRenderer(ProjectRenderPlan))
        RenderDiagnostics.drawStatsEnabled = true
        repeat(FRAMES) { game.update(FRAME, WIDTH, HEIGHT) }
        return checkNotNull(RenderDiagnostics.drawStats) { "the scene was not planned with stats on" }
    }

    /** A camera looking down -z at [COPIES] rocks side by side at each of [DISTANCES]. */
    private fun scene(): String {
        val rocks = DISTANCES.flatMapIndexed { row, distance ->
            List(COPIES) { copy ->
                """{ "name": "Rock-$row-$copy", "transform": { "position": { "x": ${(copy - 1) * 0.5}, "y": 0.0, "z": ${-distance} } },
                     "components": [ { "component": "mesh_renderer", "mesh": "$ROCK", "material": "lit-shadow" } ] }"""
            }
        }
        return """
        { "version": 1, "name": "rows", "nodes": [
          { "name": "Camera", "components": [ { "component": "camera", "eye": { "x": 0.0, "y": 0.0, "z": 0.0 },
            "center": { "x": 0.0, "y": 0.0, "z": -1.0 }, "fovYDegrees": 60.0, "near": 0.1, "far": 500.0 } ] },
          ${rocks.joinToString(",\n")}
        ] }
        """.trimIndent()
    }

    private companion object {
        /** Level 0 nearer than 6.9, 1 to 14.4, 2 to 34.6, then 3, for a unit radius on a 60° lens. */
        val DISTANCES = listOf(4f, 10f, 25f, 60f)
        const val COPIES = 3
        const val FRAMES = 3
        const val ROCK = "models/rock.gltf"
        const val MANIFEST_FILE = "awake.project.json"
        const val SCENE_FILE = "scenes/main.scene.json"
        const val MANIFEST =
            """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
    }
}
