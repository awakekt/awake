/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.PlannedSystem
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneSystemPhase
import com.awakekt.awake.scene.runtime.schedule.SceneSystemObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A played project's systems reach the app's [SceneSystemObserver] by their plan names, not lumped
 * into the three scheduled systems that run them, and no mark encloses another (#1621).
 */
class ProjectSystemObserverTest {
    @Test
    fun eachPlannedSystemIsReportedByNameOncePerRunInPlanOrder() = runTest {
        val (game, runtime, planned) = played()
        val recording = Recording()
        runtime.systemObserver = recording

        game.update(STEP, WIDTH, HEIGHT)

        val names = planned.map { it.name }.toSet()
        val reported = recording.marks.filter { it.drop(1) in names }
        val frameOrder = planned.filter { it.phase != SceneSystemPhase.Fixed }.map { it.name }
        val fixedOrder = planned.filter { it.phase == SceneSystemPhase.Fixed }.map { it.name }
        val expected = (fixedOrder + frameOrder).flatMap { listOf("+$it", "-$it") }
        assertTrue(planned.size >= MIN_PLANNED, "the scene plans systems to report: $planned")
        assertEquals(expected, reported, "one pair per planned system, in plan order")
    }

    @Test
    fun noMarkEnclosesAnotherSoTheirSumsCountEachSystemOnce() = runTest {
        val (game, runtime, _) = played()
        val recording = Recording()
        runtime.systemObserver = recording

        repeat(FRAMES) { game.update(STEP, WIDTH, HEIGHT) }

        var open: String? = null
        for (mark in recording.marks) {
            val name = mark.drop(1)
            if (mark[0] == '+') {
                assertEquals(null, open, "$name begins inside $open")
                open = name
            } else {
                assertEquals(open, name, "$name ends while $open is open")
                open = null
            }
        }
        assertEquals(null, open)
        assertTrue("+project-frame" in recording.marks, "the runner's own steps are still reported")
    }

    private suspend fun played(): Triple<AwakeAppLifecycle, SceneAppLifecycleRuntime, List<PlannedSystem>> {
        val project = loadProject(files())
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())
        game.update(STEP, WIDTH, HEIGHT)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        return Triple(game, runtime, game.requireService<ProjectSystemPlan>().planned)
    }

    private class Recording : SceneSystemObserver {
        val marks = ArrayList<String>()

        override fun before(name: String) {
            marks += "+$name"
        }

        override fun after(name: String) {
            marks += "-$name"
        }
    }

    private fun files() = AssetSource { path ->
        runCatching { mapOf("awake.project.json" to MANIFEST, "scenes/main.scene.json" to SCENE).getValue(path.value).encodeToByteArray() }
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val FRAMES = 3
        const val MIN_PLANNED = 4
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """
{ "version": 1, "name": "look", "nodes": [
  { "name": "Camera", "components": [
    { "component": "camera", "primary": true },
    { "component": "camera_rig", "mode": "ThirdPerson", "target": "Spinner", "distance": 9.0, "pitch": -0.3,
      "offset": { "x": 0.0, "y": 1.5, "z": 0.0 } } ] },
  { "name": "Spinner", "components": [ { "component": "spin_control", "speed": 2.0 } ] }
] }
"""
    }
}
