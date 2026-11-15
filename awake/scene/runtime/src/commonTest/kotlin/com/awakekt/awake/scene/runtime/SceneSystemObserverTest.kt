/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.InterpolatedSystem
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.renderer.RenderDrawStage
import com.awakekt.awake.render.renderer.RenderDrawStages
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.runtime.schedule.SceneSystemObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * An installed [SceneSystemObserver] hears one before/after pair around every run of every scheduled
 * system, under names built once (#1621).
 */
class SceneSystemObserverTest {
    @Test
    fun eachPlannedSystemIsMarkedOnceARunInScheduleOrder() = runTest {
        val scene = observedScene()

        scene.frame(steps = 2)

        val pairs = scene.marks.chunked(2).onEach { (before, after) ->
            assertEquals("before", before.first)
            assertEquals("after", after.first)
            assertSame(before.second, after.second, "a pair is around one run")
        }.map { it[0].second }
        assertEquals(
            listOf(
                "physics", SceneSystemObserver.APP_UPDATE,
                "physics", SceneSystemObserver.APP_UPDATE,
                "physics.interpolate",
                "pose",
                "infrastructure:pre-frame transforms",
                "camera",
                "infrastructure:Extraction",
            ),
            pairs,
        )
    }

    @Test
    fun theSameNameObjectsComeBackEveryFrame() = runTest {
        val scene = observedScene()
        scene.frame(steps = 1)
        val first = scene.marks.associate { it.second to it.second }

        scene.frame(steps = 2)

        scene.marks.forEach { (_, name) -> assertSame(first.getValue(name), name, "$name is built once, not per call") }
    }

    @Test
    fun withNoObserverNothingIsMarked() = runTest {
        val scene = observedScene(drawing = true)
        scene.frame(steps = 1)
        scene.runtime.systemObserver = null

        scene.frame(steps = 2)

        assertTrue(scene.marks.isEmpty())
        assertEquals(null, scene.renderer.stages, "the renderer is told to stop telling anyone")
    }

    @Test
    fun theRenderersDrawStagesSplitTheSystemThatDraws() = runTest {
        val scene = observedScene(drawing = true)

        scene.frame(steps = 1)

        val drawing = scene.marks.dropWhile { it.second != "infrastructure:Extraction" }
        val extraction = "infrastructure:Extraction"
        assertEquals(
            listOf(
                "before" to extraction, "after" to extraction,
                "before" to SceneSystemObserver.RENDER_PREPARE, "after" to SceneSystemObserver.RENDER_PREPARE,
                "before" to extraction, "after" to extraction,
                "before" to SceneSystemObserver.RENDER_UPLOAD, "after" to SceneSystemObserver.RENDER_UPLOAD,
                "before" to extraction, "after" to extraction,
                "before" to SceneSystemObserver.RENDER_RECORD, "after" to SceneSystemObserver.RENDER_RECORD,
                "before" to extraction, "after" to extraction,
                "before" to SceneSystemObserver.RENDER_SUBMIT, "after" to SceneSystemObserver.RENDER_SUBMIT,
                "before" to extraction, "after" to extraction,
            ),
            drawing,
            "no mark encloses another: the system's mark is ended around each stage",
        )
    }

    private class Recording : SceneSystemObserver {
        val marks = mutableListOf<Pair<String, String>>()
        override fun before(name: String) {
            marks += "before" to name
        }
        override fun after(name: String) {
            marks += "after" to name
        }
    }

    /** Splits its draw the way a backend that reports stages does, when [Extraction] calls it. */
    private class StagedRenderer : NoopRenderer() {
        var stages: RenderDrawStages? = null
        override fun observeDrawStages(stages: RenderDrawStages?) {
            this.stages = stages
        }
        fun drawStaged() = RenderDrawStage.entries.forEach { stage ->
            stages?.begin(stage)
            stages?.end(stage)
        }
    }

    private class Scene(
        val runtime: SceneAppLifecycleRuntime,
        val app: AwakeAppLifecycle,
        val renderer: StagedRenderer,
        private val recording: Recording,
    ) {
        val marks get() = recording.marks

        fun frame(steps: Int) {
            recording.marks.clear()
            app.update(STEP * steps + STEP / 2, WIDTH, HEIGHT)
        }
    }

    private class Extraction(private val renderer: StagedRenderer?) : System {
        override fun update(world: World, delta: Float) {
            renderer?.drawStaged()
        }
    }

    private suspend fun observedScene(drawing: Boolean = false): Scene {
        val renderer = StagedRenderer()
        val idle = object : System {
            override fun update(world: World, delta: Float) = Unit
        }
        val physics = object : InterpolatedSystem {
            override fun update(world: World, delta: Float) = Unit
            override fun interpolate(world: World, alpha: Float) = Unit
        }
        val spec = SceneAppSpec(
            sceneName = "observed",
            systems = listOf(
                SceneSystemRegistration(SceneSystemHandle<System>("physics"), SceneSystemPhase.Fixed) { physics },
                SceneSystemRegistration(SceneSystemHandle<System>("pose"), SceneSystemPhase.PreAnimation) { idle },
                SceneSystemRegistration(SceneSystemHandle<System>("camera"), SceneSystemPhase.Frame) { idle },
            ),
            scenePopulationBlock = {},
            renderableFactory = { error("unused") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { listOf(Extraction(renderer.takeIf { drawing })) },
        )
        val builder = AppSpecBuilder()
        val runtime = spec.installInto(builder)
        val app = builder.build().createLifecycle().also { it.ready(renderer) }
        val recording = Recording()
        runtime.systemObserver = recording
        return Scene(runtime, app, renderer, recording)
    }

    private companion object {
        const val STEP = 1f / 60f
        const val WIDTH = 320f
        const val HEIGHT = 240f
    }
}
