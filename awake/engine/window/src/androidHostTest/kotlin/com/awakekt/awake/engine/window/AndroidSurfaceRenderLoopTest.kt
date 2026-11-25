/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.engine.platform.WindowLifecycle
import com.awakekt.awake.engine.platform.lifecycle.DisplayInsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Verifies [AndroidSurfaceRenderLoop] runs all GPU/lifecycle work on its dedicated render thread
 * rather than Android's UI thread (preventing cold-start shader-compilation ANRs in
 * `AwakeSurfaceView.surfaceCreated`), while preserving strict surface lifetime ordering.
 */
internal class AndroidSurfaceRenderLoopTest {
    @Test
    fun surfaceCreatedReturnsImmediatelyWhileCreateBlocksOnRenderThread() {
        val uiThreadName = Thread.currentThread().name
        val createStarted = CountDownLatch(1)
        val allowCreateToFinish = CountDownLatch(1)
        val firstUpdate = CountDownLatch(1)
        val lifecycle = RecordingLifecycle(
            onCreate = {
                createStarted.countDown()
                assertTrue(allowCreateToFinish.await(5, TimeUnit.SECONDS))
            },
            onUpdate = { firstUpdate.countDown() },
        )
        val loop = AndroidSurfaceRenderLoop(
            lifecycle = lifecycle,
            tickFrame = { _, step ->
                step(0.016)
                Thread.sleep(2)
            },
        )

        val startNanos = System.nanoTime()
        loop.onSurfaceCreated("surface-1")
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)

        assertTrue(elapsedMs < 500, "onSurfaceCreated must not block the UI thread (took ${elapsedMs}ms)")
        assertTrue(createStarted.await(5, TimeUnit.SECONDS), "create() should start asynchronously on the render thread")
        assertTrue(lifecycle.threadsUsed.none { it == uiThreadName }, "create() must never run on the UI thread")

        allowCreateToFinish.countDown()
        assertTrue(firstUpdate.await(5, TimeUnit.SECONDS), "render loop should begin ticking after create()")

        loop.onSurfaceDestroyed()
        loop.onDetachedFromWindow()

        assertEquals(setOf(AndroidSurfaceRenderLoop.DEFAULT_THREAD_NAME), lifecycle.threadsUsed.toSet())
        assertEquals(listOf("create", "update", "releaseSurface", "dispose"), lifecycle.events.distinct())
    }

    @Test
    fun surfaceChangedAndInsetsArrivingDuringSlowCreateApplyBeforeFirstUpdate() {
        val createStarted = CountDownLatch(1)
        val allowCreate = CountDownLatch(1)
        val firstUpdate = CountDownLatch(1)
        val lifecycle = RecordingLifecycle(
            onCreate = {
                createStarted.countDown()
                assertTrue(allowCreate.await(5, TimeUnit.SECONDS))
            },
            onUpdate = { firstUpdate.countDown() },
        )
        val loop = AndroidSurfaceRenderLoop(
            lifecycle = lifecycle,
            tickFrame = { _, step ->
                step(0.016)
                Thread.sleep(2)
            },
        )

        loop.onSurfaceCreated("surface-1")
        assertTrue(createStarted.await(5, TimeUnit.SECONDS))

        val safe = DisplayInsets(top = 24f, bottom = 16f)
        val gestures = DisplayInsets(left = 12f, right = 12f)
        val ime = DisplayInsets(bottom = 240f)
        loop.onInsetsChanged(safe, gestures, ime)
        loop.onSurfaceChanged(density = 2.75f, dpi = 420f, width = 1080, height = 2400)

        allowCreate.countDown()
        assertTrue(firstUpdate.await(5, TimeUnit.SECONDS))

        loop.onSurfaceDestroyed()
        loop.onDetachedFromWindow()

        val firstUpdateIdx = lifecycle.events.indexOf("update")
        assertTrue(firstUpdateIdx > 0, "expected update after create and display setup: ${lifecycle.events}")
        assertTrue(lifecycle.events.indexOf("setDensity") in 1 until firstUpdateIdx)
        assertTrue(lifecycle.events.indexOf("setDpi") in 1 until firstUpdateIdx)
        assertTrue(lifecycle.events.indexOf("setSafeArea") in 1 until firstUpdateIdx)
        assertTrue(lifecycle.events.indexOf("resize:1080x2400") in 1 until firstUpdateIdx)
        assertEquals(2.75f, lifecycle.lastDensity)
        assertEquals(420f, lifecycle.lastDpi)
        assertEquals(safe, lifecycle.lastSafeArea)
        assertEquals(gestures, lifecycle.lastGestures)
        assertEquals(ime, lifecycle.lastIme)
        assertEquals(setOf(AndroidSurfaceRenderLoop.DEFAULT_THREAD_NAME), lifecycle.threadsUsed.toSet())
    }

    @Test
    fun surfaceDestroyedDuringSlowCreateReleasesImmediatelyWithoutDrawingFrames() {
        val createStarted = CountDownLatch(1)
        val allowCreate = CountDownLatch(1)
        val updateCount = AtomicInteger(0)
        val lifecycle = RecordingLifecycle(
            onCreate = {
                createStarted.countDown()
                assertTrue(allowCreate.await(5, TimeUnit.SECONDS))
            },
            onUpdate = { updateCount.incrementAndGet() },
        )
        val loop = AndroidSurfaceRenderLoop(
            lifecycle = lifecycle,
            tickFrame = { _, step -> step(0.016) },
        )

        loop.onSurfaceCreated("surface-1")
        assertTrue(createStarted.await(5, TimeUnit.SECONDS))

        val destroyReturned = CountDownLatch(1)
        val destroyer = Thread {
            loop.onSurfaceDestroyed()
            destroyReturned.countDown()
        }.apply { start() }

        // onSurfaceDestroyed must wait for create() + releaseSurface() before returning.
        assertFalse(destroyReturned.await(50, TimeUnit.MILLISECONDS))

        allowCreate.countDown()
        assertTrue(destroyReturned.await(5, TimeUnit.SECONDS))
        destroyer.join(5_000)
        loop.onDetachedFromWindow()

        assertEquals(0, updateCount.get(), "must not tick frames after surfaceDestroyed arrived mid-create")
        assertEquals(listOf("create", "releaseSurface", "dispose"), lifecycle.events)
        assertEquals(setOf(AndroidSurfaceRenderLoop.DEFAULT_THREAD_NAME), lifecycle.threadsUsed.toSet())
    }

    @Test
    fun restoreSurfaceRunsOffUiThreadOnBackgroundResume() {
        val uiThreadName = Thread.currentThread().name
        val firstFrame = CountDownLatch(1)
        val restoreStarted = CountDownLatch(1)
        val allowRestore = CountDownLatch(1)
        val resumedFrame = CountDownLatch(1)
        val restored = AtomicReference(false)
        val lifecycle = RecordingLifecycle(
            onRestore = {
                restoreStarted.countDown()
                assertTrue(allowRestore.await(5, TimeUnit.SECONDS))
                restored.set(true)
            },
            onUpdate = {
                firstFrame.countDown()
                if (restored.get()) resumedFrame.countDown()
            },
        )
        val loop = AndroidSurfaceRenderLoop(
            lifecycle = lifecycle,
            tickFrame = { _, step ->
                step(0.016)
                Thread.sleep(2)
            },
        )

        loop.onSurfaceCreated("surface-1")
        assertTrue(firstFrame.await(5, TimeUnit.SECONDS))
        loop.onSurfaceDestroyed()

        val startNanos = System.nanoTime()
        loop.onSurfaceCreated("surface-2")
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        assertTrue(elapsedMs < 500, "restoreSurface must not block the UI thread (took ${elapsedMs}ms)")
        assertTrue(restoreStarted.await(5, TimeUnit.SECONDS))

        allowRestore.countDown()
        assertTrue(resumedFrame.await(5, TimeUnit.SECONDS))

        loop.onSurfaceDestroyed()
        loop.onDetachedFromWindow()

        assertEquals(1, lifecycle.events.count { it == "create" })
        assertEquals(1, lifecycle.events.count { it == "restoreSurface" })
        assertEquals(2, lifecycle.events.count { it == "releaseSurface" })
        assertEquals(1, lifecycle.events.count { it == "dispose" })
        assertNotEquals(uiThreadName, lifecycle.threadsUsed.first())
        assertEquals(setOf(AndroidSurfaceRenderLoop.DEFAULT_THREAD_NAME), lifecycle.threadsUsed.toSet())
    }

    @Test
    fun createExceptionDoesNotDeadlockDestroyOrDetach() {
        val createAttempted = CountDownLatch(1)
        val lifecycle = RecordingLifecycle(
            onCreate = {
                createAttempted.countDown()
                error("simulated backend failure")
            },
        )
        val loop = AndroidSurfaceRenderLoop(
            lifecycle = lifecycle,
            tickFrame = { _, step -> step(0.016) },
        )

        val uncaught = AtomicReference<Throwable?>(null)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> uncaught.set(throwable) }
        try {
            loop.onSurfaceCreated("surface-1")
            assertTrue(createAttempted.await(5, TimeUnit.SECONDS))
            loop.onSurfaceDestroyed()
            loop.onDetachedFromWindow()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }

        assertEquals("simulated backend failure", uncaught.get()?.message)
    }

    @Suppress("TooManyFunctions") // One override per WindowLifecycle callback.
    private class RecordingLifecycle(
        private val onCreate: () -> Unit = {},
        private val onRestore: () -> Unit = {},
        private val onUpdate: () -> Unit = {},
    ) : WindowLifecycle {
        override val input: Input = Input()
        val events = CopyOnWriteArrayList<String>()
        val threadsUsed = CopyOnWriteArrayList<String>()

        @Volatile var lastDensity: Float = 0f

        @Volatile var lastDpi: Float = 0f

        @Volatile var lastSafeArea: DisplayInsets = DisplayInsets.None

        @Volatile var lastGestures: DisplayInsets = DisplayInsets.None

        @Volatile var lastIme: DisplayInsets = DisplayInsets.None

        private fun record(name: String) {
            threadsUsed += Thread.currentThread().name
            events += name
        }

        override fun create(surface: Any?) {
            record("create")
            onCreate()
        }

        override fun update(delta: Float) {
            record("update")
            onUpdate()
        }

        override fun pause() = record("pause")

        override fun resume() = record("resume")

        override fun resize(x: Int, y: Int, width: Int, height: Int) = record("resize:${width}x$height")

        override fun dispose() = record("dispose")

        override fun releaseSurface() = record("releaseSurface")

        override fun restoreSurface(surface: Any) {
            record("restoreSurface")
            onRestore()
        }

        override fun setDensity(density: Float) {
            record("setDensity")
            lastDensity = density
        }

        override fun setDpi(dpi: Float) {
            record("setDpi")
            lastDpi = dpi
        }

        override fun setSafeArea(insets: DisplayInsets) {
            record("setSafeArea")
            lastSafeArea = insets
        }

        override fun setSystemGestureInsets(insets: DisplayInsets) {
            record("setSystemGestureInsets")
            lastGestures = insets
        }

        override fun setImeInsets(insets: DisplayInsets) {
            record("setImeInsets")
            lastIme = insets
        }
    }
}
