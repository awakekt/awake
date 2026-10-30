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
import com.awakekt.awake.engine.platform.error.AppError
import com.awakekt.awake.engine.platform.error.AppErrorHandler
import com.awakekt.awake.engine.platform.error.AppErrorSource
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A system that throws in the frame loop reaches the app's [AppErrorHandler] once, and the app keeps
 * running: the system is skipped from then on and everything else in the frame still runs. With no
 * handler, the exception is thrown as before.
 */
class SystemFailureRoutingTest {
    private val received = mutableListOf<AppError>()
    private val handler = AppErrorHandler { received += it }

    @Test
    fun aFixedSystemThatThrowsIsReportedOnceSkippedAndItsNeighboursKeepRunning() = runTest {
        val boom = IllegalStateException("bad physics step")
        val broken = Probe(failWith = boom)
        val neighbour = Probe()
        val app = playing(handler, Fixed("broken", broken), Fixed("neighbour", neighbour))

        repeat(FRAMES) { app.frame() }

        val error = received.single()
        assertEquals(AppErrorSource.System, error.source)
        assertSame(boom, error.cause)
        assertNull(error.retry, "a system is not run again")
        assertEquals("Probe", error.subject)
        assertEquals(1, broken.calls, "the system that failed is skipped from then on")
        assertTrue(neighbour.calls >= FRAMES, "the system after it ran every step, the failing step included: ${neighbour.calls}")
    }

    @Test
    fun aFrameSystemThatThrowsIsReportedOnceAndTheRenderSystemsStillRunThatFrame() = runTest {
        val boom = IllegalArgumentException("bad camera")
        val broken = Probe(failWith = boom)
        val infra = Probe()
        val app = playing(handler, Frame("broken", broken), infrastructure = listOf(infra))

        repeat(FRAMES) { app.frame() }

        assertSame(boom, received.single().cause)
        assertEquals(1, broken.calls)
        assertTrue(infra.calls >= FRAMES, "the infrastructure after it drew every frame: ${infra.calls}")
    }

    @Test
    fun anInfrastructureSystemThatThrowsIsReportedOnceAndTheAppGoesOn() = runTest {
        val boom = IllegalStateException("bad draw")
        val broken = Probe(failWith = boom)
        val frame = Probe()
        val app = playing(handler, Frame("frame", frame), infrastructure = listOf(broken))

        repeat(FRAMES) { app.frame() }

        assertSame(boom, received.single().cause)
        assertEquals(1, broken.calls)
        assertTrue(frame.calls >= FRAMES)
    }

    @Test
    fun anInterpolatingSystemThatThrowsWhileBlendingIsReportedOnceAndSkippedWholly() = runTest {
        val boom = IllegalStateException("bad blend")
        val blending = Blending(failWith = boom)
        val app = playing(handler, Fixed("blending", blending))

        repeat(FRAMES) { app.frame() }

        assertSame(boom, received.single().cause)
        assertEquals(1, blending.interpolations, "the blend that failed is the last")
        val stepsAtFailure = blending.steps
        app.frame()
        assertEquals(stepsAtFailure, blending.steps, "and the system takes no more fixed steps either")
    }

    @Test
    fun aSystemRegisteredAsInterpolatedStillBlendsWhenItHasNotFailed() = runTest {
        val blending = Blending()
        val app = playing(handler, Fixed("blending", blending))

        repeat(FRAMES) { app.frame() }

        assertTrue(blending.interpolations >= FRAMES, "the guard passes interpolate through: ${blending.interpolations}")
        assertTrue(received.isEmpty())
    }

    @Test
    fun withoutAHandlerTheExceptionIsThrownAsBeforeEveryTimeItRecurs() = runTest {
        val boom = IllegalStateException("bad physics step")
        val broken = Probe(failWith = boom)
        val app = playing(handler = null, Fixed("broken", broken))

        assertSame(boom, assertFailsWith<IllegalStateException> { app.frame() })
        assertSame(boom, assertFailsWith<IllegalStateException> { app.frame() }, "it is not skipped when nobody was told")
        assertEquals(2, broken.calls)
    }

    @Test
    fun aSystemThatKeepsFailingNeverReachesTheHandlerTwice() = runTest {
        val broken = Probe(failWith = IllegalStateException("always"))
        val app = playing(handler, Frame("broken", broken))

        repeat(FRAMES * 10) { app.frame() }

        assertEquals(1, received.size)
    }

    private sealed interface Registered {
        val name: String
        val system: System
    }

    private class Fixed(override val name: String, override val system: System) : Registered

    private class Frame(override val name: String, override val system: System) : Registered

    private suspend fun playing(
        handler: AppErrorHandler?,
        vararg systems: Registered,
        infrastructure: List<System> = emptyList(),
    ): AwakeAppLifecycle {
        val builder = AppSpecBuilder()
        handler?.let { builder.service(AppErrorHandler::class, it) }
        SceneAppSpec(
            sceneName = "failures",
            systems = systems.map { registered ->
                SceneSystemRegistration(
                    handle = SceneSystemHandle<System>(registered.name),
                    phase = if (registered is Fixed) SceneSystemPhase.Fixed else SceneSystemPhase.Frame,
                    factory = { registered.system },
                )
            },
            scenePopulationBlock = {},
            renderableFactory = { error("unused") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { infrastructure },
        ).installInto(builder)
        return builder.build().createLifecycle().also { it.ready(NoopRenderer()) }
    }

    private fun AwakeAppLifecycle.frame() = update(STEP, WIDTH, HEIGHT)

    /** Counts its updates and throws [failWith] from each, if given. */
    private open class Probe(private val failWith: Exception? = null) : System {
        var calls = 0

        override fun update(world: World, delta: Float) {
            calls++
            failWith?.let { throw it }
        }
    }

    /** A fixed system that blends; throws [failWith] from each blend, if given. */
    private class Blending(private val failWith: Exception? = null) : InterpolatedSystem {
        var steps = 0
        var interpolations = 0

        override fun update(world: World, delta: Float) {
            steps++
        }

        override fun interpolate(world: World, alpha: Float) {
            interpolations++
            failWith?.let { throw it }
        }
    }

    private companion object {
        const val STEP = 1f / 30f
        const val WIDTH = 320f
        const val HEIGHT = 240f
        const val FRAMES = 5
    }
}
