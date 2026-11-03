/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A scene runs at most [SceneAppSpec.maxFixedStepsPerFrame] fixed steps a frame, and its runtime's
 * [SceneAppLifecycleRuntime.fixedStep] tells its frame systems what the frame ran and dropped.
 */
class SceneFixedStepTest {
    @Test
    fun aSceneRunsFiveStepsAFrameByDefaultAndItsFrameSystemsReadWhatWasDropped() = runTest {
        val scene = playing(cap = null)

        scene.app.update(1f, WIDTH, HEIGHT)

        assertEquals(5, scene.fixedSteps)
        assertEquals(5, scene.runtime.fixedStep.maxStepsPerFrame)
        assertEquals(listOf(Seen(ran = 5, dropped = 55)), scene.seen)
        assertEquals(1, scene.heard, "the listener added through the runtime hears the dropping frame")
    }

    @Test
    fun aRaisedCapRunsMoreStepsAndDropsLess() = runTest {
        val scene = playing(cap = 15)

        scene.app.update(1f, WIDTH, HEIGHT)
        scene.app.update(STEP * 3, WIDTH, HEIGHT)

        assertEquals(15 + 3, scene.fixedSteps)
        assertEquals(listOf(Seen(ran = 15, dropped = 45), Seen(ran = 3, dropped = 0)), scene.seen)
        assertEquals(45L, scene.runtime.fixedStep.droppedStepsTotal)
        assertEquals(1, scene.heard)
    }

    @Test
    fun aCapBelowOneIsRefused() {
        assertFailsWith<IllegalArgumentException> { spec(emptyList()).maxFixedStepsPerFrame = 0 }
    }

    private data class Seen(val ran: Int, val dropped: Int)

    /** A scene with one fixed system that counts its steps and one frame system that reads the report. */
    private class Playing {
        var fixedSteps = 0
        var heard = 0
        val seen = mutableListOf<Seen>()
        lateinit var runtime: SceneAppLifecycleRuntime
        lateinit var app: AwakeAppLifecycle

        private val counting = object : System {
            override fun update(world: World, delta: Float) {
                fixedSteps++
            }
        }

        suspend fun start(cap: Int?) {
            val builder = AppSpecBuilder()
            val spec = spec(
                listOf(
                    SceneSystemRegistration(SceneSystemHandle<System>("count"), SceneSystemPhase.Fixed) { counting },
                    SceneSystemRegistration(SceneSystemHandle<System>("read"), SceneSystemPhase.Frame) {
                        val scene = this
                        scene.fixedStep.addTimeDroppedListener { _, _ -> heard++ }
                        object : System {
                            override fun update(world: World, delta: Float) {
                                seen += Seen(scene.fixedStep.stepsThisFrame, scene.fixedStep.droppedSteps)
                            }
                        }
                    },
                ),
            )
            cap?.let { spec.maxFixedStepsPerFrame = it }
            runtime = spec.installInto(builder)
            app = builder.build().createLifecycle().also { it.ready(NoopRenderer()) }
            // Ready runs the frame systems once, with no fixed step, to settle the scene: not a frame.
            seen.clear()
        }
    }

    private suspend fun playing(cap: Int?): Playing = Playing().also { it.start(cap) }

    private companion object {
        const val STEP = 1f / 60f
        const val WIDTH = 320f
        const val HEIGHT = 240f

        fun spec(systems: List<SceneSystemRegistration>) = SceneAppSpec(
            sceneName = "fixed-step",
            systems = systems,
            scenePopulationBlock = {},
            renderableFactory = { error("unused") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { emptyList() },
        )
    }
}
