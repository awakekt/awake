/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.runtime.schedule.SceneSystemObserver
import kotlinx.coroutines.test.runTest
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Observing a frame's systems costs the frame no allocation, installed or not (#1621): the names
 * are built once and the marks are two virtual calls. A frame here, with [SYSTEMS] systems, measured
 * 257 B bare on main, 257 B bare and 256 B with a no-op observer here.
 */
class SceneSystemObserverAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun anObservedFrameAllocatesNoMoreThanABareOne() = runTest {
        val bare = bytesPerFrame(observer = null)
        val observed = bytesPerFrame(observer = NoOp)

        println("SceneSystemObserver: $bare B a frame bare, $observed B observed")
        assertTrue(observed <= bare + NOISE, "an observed frame allocated $observed B, a bare one $bare B")
    }

    private object NoOp : SceneSystemObserver {
        override fun before(name: String) = Unit
        override fun after(name: String) = Unit
    }

    private suspend fun bytesPerFrame(observer: SceneSystemObserver?): Long {
        val idle = object : System {
            override fun update(world: World, delta: Float) = Unit
        }
        val spec = SceneAppSpec(
            sceneName = "allocation",
            systems = List(SYSTEMS) { index ->
                val phase = SceneSystemPhase.entries[index % SceneSystemPhase.entries.size]
                SceneSystemRegistration(SceneSystemHandle<System>("system-$index"), phase) { idle }
            },
            scenePopulationBlock = {},
            renderableFactory = { error("unused") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { listOf(idle) },
        )
        val builder = AppSpecBuilder()
        val runtime = spec.installInto(builder)
        val app = builder.build().createLifecycle().also { it.ready(NoopRenderer()) }
        runtime.systemObserver = observer
        repeat(WARM_UP) { app.update(STEP, WIDTH, HEIGHT) }
        @Suppress("DEPRECATION")
        val thread = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(thread)
        repeat(FRAMES) { app.update(STEP, WIDTH, HEIGHT) }
        return (threads.getThreadAllocatedBytes(thread) - before) / FRAMES
    }

    private companion object {
        const val SYSTEMS = 30
        const val WARM_UP = 200
        const val FRAMES = 2_000
        const val STEP = 1f / 60f
        const val WIDTH = 320f
        const val HEIGHT = 240f

        /** Slack for the counter: bare and observed frames differed by 1 B, measured. */
        const val NOISE = 16L
    }
}
