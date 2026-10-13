/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.sun.management.ThreadMXBean
import kotlinx.coroutines.test.runTest
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the project runtime adds to each fixed step and frame, to be able to switch scenes, allocates
 * nothing while no switch is on its way.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class SceneSwitchAllocationProbe {
    @Test
    fun aStepAndAFrameWithNoSwitchOnItsWayAllocateNothing() = runTest {
        val files = mapOf("awake.project.json" to MANIFEST, "scenes/main.scene.json" to SCENE)
        val project = loadProject(AssetSource { path -> runCatching { files.getValue(path.value).encodeToByteArray() } })
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())
        val world = game.requireService<SceneAppLifecycleRuntime>().world
        val runner = game.requireService<SceneSwitch>() as ProjectSceneRunner

        repeat(WARMUP) { runner.tick(world) }
        val before = allocated()
        repeat(MEASURED) { runner.tick(world) }

        assertEquals(0L, (allocated() - before) / MEASURED)
        project.close()
    }

    /** One fixed step, its interpolation and one frame of the runner's own systems. */
    private fun ProjectSceneRunner.tick(world: World) {
        fixedSystems.update(world, STEP)
        fixedSystems.interpolate(world, HALF)
        frameSystems.update(world, STEP)
    }

    private fun allocated(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val HALF = 0.5f
        const val WARMUP = 5_000
        const val MEASURED = 2_000
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Camera", "components": [ { "component": "camera" } ] },
  { "name": "Crate", "components": [ { "component": "meshRenderer", "mesh": "cube", "material": "lit-shadow" } ] }
] }
"""
    }
}
