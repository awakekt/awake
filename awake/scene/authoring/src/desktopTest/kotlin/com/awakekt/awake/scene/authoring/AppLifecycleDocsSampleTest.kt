/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.appModule
import com.awakekt.awake.engine.bootstrap.dsl.appSpec
import com.awakekt.awake.engine.bootstrap.dsl.module
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// --8<-- [start:frame-counter]
class FrameCounter {
    var frames = 0
}
// --8<-- [end:frame-counter]

/** Records each call, so the test can check the order the page describes. */
private class OrderProbe(private val name: String, private val trace: MutableList<String>) : System {
    override fun update(world: World, delta: Float) {
        trace += name
    }
}

/**
 * The "App lifecycle" guide includes its samples from here, so this test keeps them compiling and
 * checks the call order the page describes.
 */
class AppLifecycleDocsSampleTest {

    @Test
    fun anAppRunsItsCallbacksInLifecycleOrder() = runTest {
        val log = mutableListOf<String>()
        // --8<-- [start:app]
        val game = app {
            window {
                title = "Harbor Town"
                size(1280, 720)
                backend.vulkan()
            }
            ready { renderer -> log += "ready" }
            render { frame -> log += "frame ${frame.delta}" }
            dispose { log += "dispose" }
        }
        // --8<-- [end:app]

        assertEquals("Harbor Town", game.windowConfig.title)
        assertEquals(1280, game.windowConfig.width)
        assertEquals(AppWindowBackend.VULKAN, game.windowConfig.backend)

        // --8<-- [start:drive]
        game.ready(renderer) // a host calls this once its renderer exists
        game.update(delta = 0.016f, viewportWidth = 320f, viewportHeight = 240f)
        game.dispose()
        // --8<-- [end:drive]

        assertEquals(listOf("ready", "frame 0.016", "dispose"), log)
    }

    @Test
    fun aModuleCarriesItsOwnServicesAndCallbacks() = runTest {
        // --8<-- [start:module]
        val stats = appModule {
            val counter = FrameCounter()
            service(FrameCounter::class, counter)
            render { counter.frames += 1 }
        }

        val game = app {
            window { title = "With stats" }
            module(stats)
        }
        // --8<-- [end:module]

        game.ready(renderer)
        game.update(0.016f, 320f, 240f)
        game.update(0.016f, 320f, 240f)
        assertEquals(2, game.requireService<FrameCounter>().frames)

        // --8<-- [start:app-spec]
        val spec = appSpec {
            window { size(320, 240) }
            module(stats)
        }
        val lifecycle = spec.createLifecycle()
        // --8<-- [end:app-spec]
        assertEquals(320, spec.windowConfig.width)
        assertEquals(0, lifecycle.requireService<FrameCounter>().frames)
    }

    @Test
    fun aSceneRunsFixedStepsThenFrameSystems() = runTest {
        val trace = mutableListOf<String>()
        // --8<-- [start:scene-systems]
        val game = app {
            scene("harbor-town") {
                fixedSystem("physics") { OrderProbe("fixed", trace) }
                frameSystem("camera") { OrderProbe("frame", trace) }
                update { step, input -> trace += "update" }
                onReady { trace += "ready" }
                onDispose { trace += "dispose" }
            }
        }
        // --8<-- [end:scene-systems]

        game.ready(renderer)
        assertEquals(listOf("ready", "frame"), trace, "ready runs, then one frame pass with delta 0")
        trace.clear()

        game.update(0.001f, 320f, 240f)
        assertEquals(listOf("frame"), trace, "less than one fixed step: no fixed work")
        trace.clear()

        game.update(2.5f / 60f, 320f, 240f)
        assertEquals(listOf("fixed", "update", "fixed", "update", "frame"), trace)
        trace.clear()

        game.update(1f, 320f, 240f)
        assertEquals(5, trace.count { it == "fixed" }, "at most five fixed steps per frame")
        trace.clear()

        game.dispose()
        assertEquals(listOf("dispose"), trace)
    }

    @Test
    fun theSceneRuntimeIsAServiceOfTheApp() = runTest {
        val game = app { scene("harbor-town") { } }
        game.ready(renderer)
        assertEquals("harbor-town", game.requireService<SceneAppLifecycleRuntime>().sceneName)
        game.dispose()
    }

    private val renderer = RecordingRenderer()
}
