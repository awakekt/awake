/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.host.FixedStepReport
import com.awakekt.awake.core.host.FixedTimestepLoop
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * A game's systems reach the fixed-step loop they run in through [SceneHostServices.fixedStep]: under
 * `runProject` it is the scene's own, capped where the game configures its scene, and a host that runs
 * the systems itself passes its own loop.
 */
class ProjectFixedStepTest {
    @Test
    fun aGamesSystemsReadTheStepsTheScenesLoopRanAndDroppedUnderRunProject() = runTest {
        val clock = ClockCapability()
        val project = loadProject(files(), capabilities = listOf(clock))
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())

        game.update(1f, WIDTH, HEIGHT)

        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        assertSame(runtime.fixedStep, clock.report, "the services give the scene's own loop")
        assertEquals(5, clock.fixedSteps, "five steps a frame unless the scene raises it")
        assertEquals(Frame(ran = 5, dropped = 55), clock.lastFrame)
        assertEquals(listOf(55), clock.heard)
    }

    @Test
    fun aSceneThatRaisesTheCapRunsMoreStepsAFrame() = runTest {
        val clock = ClockCapability()
        val project = loadProject(files(), capabilities = listOf(clock))
        val game = app {
            scene("play") {
                maxFixedStepsPerFrame(15)
                runProject(project)
            }
        }
        game.ready(TestRenderer())

        game.update(1f, WIDTH, HEIGHT)

        assertEquals(15, clock.fixedSteps)
        assertEquals(Frame(ran = 15, dropped = 45), clock.lastFrame)
        assertEquals(listOf(45), clock.heard)
    }

    @Test
    fun aHostThatStepsTheSystemsItselfPassesItsOwnLoop() = runTest {
        val clock = ClockCapability()
        val project = loadProject(files(), capabilities = listOf(clock), hasRenderer = false)
        val loop = FixedTimestepLoop(maxStepsPerFrame = 3)

        project.sceneSystems(::idle, fixedStep = loop).close()
        assertSame(loop, clock.report, "a game server or a test gives its systems the loop it steps them in")

        project.sceneSystems(::idle).close()
        assertNull(clock.report, "a host that gives no loop gives none")

        project.close()
    }

    private data class Frame(val ran: Int, val dropped: Int)

    /** A game's capability whose systems count the fixed steps and read the loop's report each frame. */
    private class ClockCapability : SceneFeature {
        override val id = "com.example.harbor-town.clock"
        var report: FixedStepReport? = null
        var fixedSteps = 0
        var lastFrame: Frame? = null
        val heard = mutableListOf<Int>()

        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
            plan.fixed("clock-steps") {
                object : System {
                    override fun update(world: World, delta: Float) {
                        fixedSteps++
                    }
                }
            }
            plan.frame("clock-read") { services ->
                report = services.fixedStep
                services.fixedStep?.addTimeDroppedListener { _, steps -> heard += steps }
                object : System {
                    override fun update(world: World, delta: Float) {
                        services.fixedStep?.let { lastFrame = Frame(it.stepsThisFrame, it.droppedSteps) }
                    }
                }
            }
        }
    }

    private fun idle() = GameplayInput(Input().currentSnapshot, InputOwnership())

    private fun files() = AssetSource { path ->
        runCatching { mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to SCENE).getValue(path.value).encodeToByteArray() }
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """{ "version": 1, "name": "harbor", "nodes": [ { "name": "Dock", "components": [] } ] }"""
    }
}
