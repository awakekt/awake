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
        val scene = observedScene()
        scene.runtime.systemObserver = null

        scene.frame(steps = 2)

        assertTrue(scene.marks.isEmpty())
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

    private class Scene(val runtime: SceneAppLifecycleRuntime, val app: AwakeAppLifecycle, private val recording: Recording) {
        val marks get() = recording.marks

        fun frame(steps: Int) {
            recording.marks.clear()
            app.update(STEP * steps + STEP / 2, WIDTH, HEIGHT)
        }
    }

    private class Extraction : System {
        override fun update(world: World, delta: Float) = Unit
    }

    private suspend fun observedScene(): Scene {
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
            infrastructureSystemsFactory = { listOf(Extraction()) },
        )
        val builder = AppSpecBuilder()
        val runtime = spec.installInto(builder)
        val app = builder.build().createLifecycle().also { it.ready(NoopRenderer()) }
        val recording = Recording()
        runtime.systemObserver = recording
        return Scene(runtime, app, recording)
    }

    private companion object {
        const val STEP = 1f / 60f
        const val WIDTH = 320f
        const val HEIGHT = 240f
    }
}
