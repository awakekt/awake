/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.FrameThreadGuard
import com.awakekt.awake.ecs.World
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * In a debug build, the frame-thread-only parts of a scene throw when another thread touches them, and
 * stay silent on the frame thread. Gradle runs tests with assertions on, which is how the JVM tells a
 * debug run apart, so the checks are on here as they are in a debug app.
 */
class FrameThreadCheckTest : PrewarmFixture() {
    @Test
    fun aDebugBuildChecksTheFrameThread() {
        assertTrue(FrameThreadGuard.enabled, "a test run has assertions on, so the checks start on")
    }

    @Test
    fun theFrameThreadUsesTheAssetLibraryAndTheWorldSilently() {
        library.requireMesh(runtime, "hull")
        library.requireMaterial(runtime, "lit")
        val entity = runtime.world.create()
        runtime.world.destroy(entity)
        runtime.prewarm(BOAT).close()
    }

    @Test
    fun anotherThreadBuildingAMeshFails() {
        val failure = offFrame { library.requireMesh(runtime, "hull") }

        assertIs<IllegalStateException>(failure)
        assertTrue(failure.message.orEmpty().startsWith("SceneAssetLibrary.requireMesh ran off the frame thread"), failure.message)
        assertEquals(null, built["hull"], "nothing was built")
    }

    @Test
    fun anotherThreadBuildingAMaterialFails() {
        val failure = offFrame { library.requireMaterial(runtime, "lit") }

        assertTrue(failure?.message.orEmpty().startsWith("SceneAssetLibrary.requireMaterial ran off the frame thread"), failure?.message)
    }

    @Test
    fun anotherThreadClosingAPrewarmFails() {
        val prewarm = runtime.prewarm(BOAT)

        val failure = offFrame { prewarm.close() }

        assertTrue(failure?.message.orEmpty().startsWith("ScenePrewarm.close ran off the frame thread"), failure?.message)
        prewarm.close()
    }

    @Test
    fun anotherThreadChangingTheWorldsStructureFails() {
        val world = runtime.world
        val entity = world.create().also { world.add(it, Marker()) }

        assertTrue(offFrame { world.create() }?.message.orEmpty().startsWith("World.create ran off the frame thread"))
        assertTrue(offFrame { world.add(entity, Marker()) }?.message.orEmpty().startsWith("World.add ran off the frame thread"))
        assertTrue(offFrame { world.remove<Marker>(entity) }?.message.orEmpty().startsWith("World.remove ran off the frame thread"))
        assertTrue(offFrame { world.destroy(entity) }?.message.orEmpty().startsWith("World.destroy ran off the frame thread"))
        assertTrue(world.isAlive(entity) && world.has<Marker>(entity), "the changes were refused")
    }

    /** Only a world the scene runtime bound is checked: one a test or a tool builds itself is not. */
    @Test
    fun aWorldNobodyBoundIsNotChecked() {
        val world = World()

        assertNull(offFrame { world.create() })
    }

    private class Marker

    /** Runs [block] on a thread of its own and answers what it threw, or null. */
    private fun offFrame(block: () -> Unit): Throwable? {
        var thrown: Throwable? = null
        thread { thrown = runCatching(block).exceptionOrNull() }.join()
        return thrown
    }
}
