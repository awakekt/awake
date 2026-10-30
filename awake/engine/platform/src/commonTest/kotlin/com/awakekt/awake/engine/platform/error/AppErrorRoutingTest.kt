/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.error

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.engine.platform.GraphicsEngine
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Core catches a failure and hands it to the app's [AppErrorHandler]; with none registered, nothing
 * changes. Each test here uses [Dispatchers.Unconfined], so work runs up to its first suspension at once.
 */
class AppErrorRoutingTest {
    private val received = mutableListOf<AppError>()
    private val handler = AppErrorHandler { received += it }

    @Test
    fun aBackendThatFailsToStartReachesTheHandlerOnceAndTheAppKeepsRunning() {
        val boom = IllegalStateException("no adapter")
        val frames = mutableListOf<Float>()
        val app = AppSpecBuilder().apply {
            service(AppErrorHandler::class, handler)
            render { frames += it.delta }
        }.build().createLifecycle()
        val engine = TestEngine(app, backendFailure = boom)

        engine.create("surface")
        engine.update(1f)

        val error = received.single()
        assertEquals(AppErrorSource.Bootstrap, error.source)
        assertSame(boom, error.cause)
        assertNull(error.retry, "the engine's own start-up is not run again over a half-built backend")
        assertSame(boom, engine.startupError, "startupError is still set, as before")
        assertFalse(engine.isReady)
        assertTrue(frames.isEmpty(), "no frame plays on an engine that did not start, and the process goes on")
    }

    @Test
    fun anAppReadyCallbackThatThrowsReachesTheHandlerOnce() {
        val boom = IllegalArgumentException("bad manifest")
        val app = AppSpecBuilder().apply {
            service(AppErrorHandler::class, handler)
            ready { throw boom }
        }.build().createLifecycle()
        val engine = TestEngine(app)

        engine.create("surface")

        val error = received.single()
        assertEquals(AppErrorSource.Bootstrap, error.source)
        assertSame(boom, error.cause)
        assertSame(boom, engine.startupError)
    }

    @Test
    fun withoutAHandlerAFailedStartIsLoggedAndKeptAsStartupErrorAsBefore() {
        val boom = IllegalStateException("no adapter")
        val engine = TestEngine(AppSpecBuilder().build().createLifecycle(), backendFailure = boom)

        engine.create("surface")
        engine.update(1f)

        assertSame(boom, engine.startupError)
        assertFalse(engine.isReady)
    }

    @Test
    fun aHandlerThatThrowsDoesNotTakeTheAppDownWithIt() {
        val app = AppSpecBuilder().apply {
            service(AppErrorHandler::class, AppErrorHandler { error("the panel itself broke") })
        }.build().createLifecycle()
        val engine = TestEngine(app, backendFailure = IllegalStateException("no adapter"))

        engine.create("surface")

        assertNotNull(engine.startupError, "the original failure is still the startup error")
    }

    @Test
    fun anAppCoroutineThatThrowsReachesTheHandlerOnceAndItsSiblingsKeepRunning() = runTest {
        val services = AppSpecBuilder().apply { service(AppErrorHandler::class, handler) }.serviceLookup()
        val scope = AppCoroutineScope(services, Dispatchers.Unconfined)
        val boom = IllegalStateException("socket closed")
        val sibling = Job()
        var siblingDone = false

        scope.launchNoStep { sibling.join(); siblingDone = true }
        scope.launchNoStep { throw boom }
        sibling.complete()

        val error = received.single()
        assertEquals(AppErrorSource.Coroutine, error.source)
        assertSame(boom, error.cause)
        assertNull(error.retry, "a coroutine is not run again")
        assertTrue(siblingDone, "a supervisor scope: the sibling is not cancelled by the failure")
        var later = false
        scope.launchNoStep { later = true }
        assertTrue(later, "and the scope still takes new work")
        scope.close()
    }

    @Test
    fun cancellingAnAppCoroutineIsNotAFailure() = runTest {
        val services = AppSpecBuilder().apply { service(AppErrorHandler::class, handler) }.serviceLookup()
        val scope = AppCoroutineScope(services, Dispatchers.Unconfined)
        val job = scope.launchNoStep { Job().join() }

        job.cancel()
        scope.close()

        assertTrue(received.isEmpty())
    }

    @Test
    fun theHandlerMayBeRegisteredAfterTheScopeExists() = runTest {
        val builder = AppSpecBuilder()
        val scope = AppCoroutineScope(builder.serviceLookup(), Dispatchers.Unconfined)
        builder.service(AppErrorHandler::class, handler)

        scope.launchNoStep { throw IllegalStateException("late") }

        assertEquals(1, received.size)
    }

    @Test
    fun withoutAHandlerAnAppCoroutineFailureIsRethrownToTheUncaughtExceptionHook() {
        val services = AppSpecBuilder().serviceLookup()
        val scope = AppCoroutineScope(services, Dispatchers.Unconfined)

        // runTest reports an exception that reaches the uncaught-exception hook as the test's failure.
        val failure = assertFailsWith<IllegalStateException> {
            runTest { scope.launchNoStep { throw IllegalStateException("nobody listens") } }
        }

        assertEquals("nobody listens", failure.message)
    }

    @Test
    fun aFailedStepReachesTheHandlerOnceAndItsRetryRunsItAgain() = runTest {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        var attempts = 0
        val results = mutableListOf<String>()

        scope.launchStep(AppErrorSource.ProjectLoad, handler, subject = "world", onDone = results::add) {
            attempts++
            if (attempts == 1) error("token refused")
            "loaded on attempt $attempts"
        }

        val error = received.single()
        assertEquals(AppErrorSource.ProjectLoad, error.source)
        assertEquals("world", error.subject)
        assertEquals("token refused", error.cause.message)
        assertTrue(results.isEmpty())
        val retry = assertNotNull(error.retry)

        retry()

        assertEquals(2, attempts, "the retry ran the step again")
        assertEquals(listOf("loaded on attempt 2"), results)
        assertEquals(1, received.size, "a step that succeeds reports nothing more")
    }

    @Test
    fun aRetryThatFailsAgainReportsANewError() = runTest {
        val scope = CoroutineScope(Dispatchers.Unconfined)

        scope.launchStep<Unit>(AppErrorSource.Bootstrap, handler) { throw IllegalStateException("still down") }
        assertIs<() -> Unit>(received.single().retry).invoke()

        assertEquals(2, received.size)
        assertEquals(AppErrorSource.Bootstrap, received.last().source)
    }

    @Test
    fun withoutAHandlerAFailedStepFailsTheCoroutineAsALaunchWould() {
        val failure = assertFailsWith<IllegalStateException> {
            runTest {
                CoroutineScope(Dispatchers.Unconfined).launchStep<Unit>(AppErrorSource.Bootstrap, errors = null) {
                    throw IllegalStateException("no handler")
                }
            }
        }

        assertEquals("no handler", failure.message)
    }

    private fun CoroutineScope.launchNoStep(block: suspend () -> Unit): Job = launch { block() }

    private class TestEngine(
        app: com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle,
        private val backendFailure: Throwable? = null,
    ) : GraphicsEngine(app) {
        override suspend fun createBackendResources(window: Any): BackendResources {
            backendFailure?.let { throw it }
            return BackendResources(renderer = NoopRenderer, viewportSize = { 1f to 1f })
        }

        override fun destroyBackend() = Unit
    }

    private object NoopRenderer : Renderer {
        override val clipSpace: ClipSpace = ClipSpace.WebGpu
        override var clearColor: Color = Color.Black
        override var wireframe: Boolean = false

        override fun createMesh(geometry: MeshGeometry): Mesh = object : Mesh {
            override val format: VertexFormat = geometry.format
            override val sizeBytes: Long = 0
            override fun destroy() = Unit
        }

        override fun createMaterial(
            texture: TextureAsset?,
            renderTarget: RenderTarget?,
            uniformFloatCount: Int,
            pbrTextures: PbrTextureSet?,
        ): Material = object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        }

        override fun createRenderTarget(width: Int, height: Int): RenderTarget = object : RenderTarget {
            override val width: Int = width
            override val height: Int = height
            override fun destroy() = Unit
        }

        override suspend fun readPixels(target: RenderTarget): TextureAsset = TextureAsset(ByteArray(0), 0, 0)
        override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) = Unit
        override fun drawDebugLines(lines: List<LineSegment>) = Unit
        override fun destroy() = Unit
    }
}
