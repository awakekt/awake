/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime.schedule

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.InterpolatedSystem
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.platform.error.AppErrorSource
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime

/**
 * This system, run so that a failure in the frame loop is handed to the app's `AppErrorHandler` and
 * the app keeps running. A host that runs scene systems itself wraps each one it builds with this.
 *
 * - **A failure is reported once, then the system is skipped.** The first exception from `update` (or
 *   `interpolate`) goes to the handler as `AppErrorSource.System`, with the exception as its cause and
 *   the system's class name as its subject, and the system is not called again. The rest of the frame
 *   runs: the system's neighbours, the render systems and the UI. A system that failed may have left
 *   half of its work done, and one that throws every frame would otherwise call the handler 60 times a
 *   second. The system runs again with the next scene, whose systems are built afresh; there is no
 *   retry for a system.
 * - **With no handler registered, nothing changes.** The exception is thrown to the caller, every
 *   frame it recurs, as it was before this existed.
 * - **No cost on the happy path.** Wrapping allocates once, here. Each call then reads one flag and
 *   enters a `try` that costs nothing until something is thrown.
 *
 * Only `Exception`s are contained; an `Error` such as running out of memory still ends the frame.
 * A system that implements [InterpolatedSystem] is wrapped as one, so the host's interpolation runs
 * through the same guard and the same "skipped" flag.
 *
 * @param runtime The scene runtime, whose app services hold the handler.
 * @return The guarded system, which is not [this] but delegates to it.
 */
fun System.guardedFor(runtime: SceneAppLifecycleRuntime): System =
    if (this is InterpolatedSystem) GuardedInterpolatedSystem(this, runtime) else GuardedSystem(this, runtime)

private open class GuardedSystem(private val inner: System, private val runtime: SceneAppLifecycleRuntime) : System {
    private var skipped = false

    // TooGenericExceptionCaught: a system, whatever it is made of, must not end the frame loop when the app has a handler.
    @Suppress("TooGenericExceptionCaught")
    override fun update(world: World, delta: Float) {
        if (skipped) return
        try {
            inner.update(world, delta)
        } catch (failure: Exception) {
            contain(failure)
        }
    }

    /** Reports [failure] and skips the system from now on, or rethrows it when the app has no handler. */
    protected fun contain(failure: Exception) {
        // Before the handler runs, so a handler that steps the frame again does not report the system twice.
        skipped = true
        val name = inner::class.simpleName ?: inner.toString()
        if (!runtime.reportError(AppErrorSource.System, failure, subject = name)) {
            skipped = false
            throw failure
        }
        log.error(failure) { "System $name failed and is skipped from now on: ${failure.message}" }
    }

    protected val isSkipped: Boolean get() = skipped
}

private class GuardedInterpolatedSystem(
    private val target: InterpolatedSystem,
    runtime: SceneAppLifecycleRuntime,
) : GuardedSystem(target, runtime),
    InterpolatedSystem {
    // TooGenericExceptionCaught: as in update.
    @Suppress("TooGenericExceptionCaught")
    override fun interpolate(world: World, alpha: Float) {
        if (isSkipped) return
        try {
            target.interpolate(world, alpha)
        } catch (failure: Exception) {
            contain(failure)
        }
    }
}

private val log = Logger("scene.systems")
