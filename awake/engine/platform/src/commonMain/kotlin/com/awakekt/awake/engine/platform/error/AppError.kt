/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.error

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup

/** Where in an app a failure happened, so a handler can decide what it means and whether to offer a retry. */
enum class AppErrorSource {
    /** The engine or the app's `ready` callbacks failed to start, or a step the app launched with [launchStep] did. */
    Bootstrap,

    /** A project, or the content a route plays, could not be loaded. */
    ProjectLoad,

    /**
     * A request to play another scene could not be carried out. A scene that fails before the one playing
     * is taken out leaves that one playing. One that fails after, once there is no way back, is reported
     * and the app goes on with the scene it has: the new one when it was put in, else the one that was
     * active, else none until the retry.
     */
    SceneSwitch,

    /** A coroutine launched in an [AppCoroutineScope] threw. */
    Coroutine,

    /**
     * Something the frame loop runs threw: a system, the app's update block (subject `updateBlock`) or its
     * UI build (subject `ui`). The app keeps running. A system or the update block is skipped from then on;
     * the UI build draws nothing until it succeeds again.
     */
    System,
}

/**
 * One failure Core caught and handed to the app, which decides what to show. Core never presents it.
 *
 * @property source Where it failed.
 * @property cause What was thrown.
 * @property retry Runs the failed step again, or null when running it again is not meaningful. It
 * returns at once; the step runs on, and a second failure reaches the handler as a new [AppError].
 * Call it on the frame thread, where the handler is called for the sources that have a frame.
 * - [AppErrorSource.ProjectLoad]: loads the project again.
 * - [AppErrorSource.SceneSwitch]: asks for the scene again.
 * - [AppErrorSource.Bootstrap], [AppErrorSource.Coroutine] and [AppErrorSource.System]: null, except
 *   for a step the app launched with [launchStep], which runs again. The engine's own start-up cannot
 *   run again, because the backend it half built is not safe to build over.
 * @property subject What failed, for a message: a scene path, a route id or a system's class name; null
 * when the [source] says it all.
 */
class AppError(
    val source: AppErrorSource,
    val cause: Throwable,
    val retry: (() -> Unit)? = null,
    val subject: String? = null,
) {
    override fun toString(): String = "AppError($source${subject?.let { " $it" }.orEmpty()}: $cause)"
}

/**
 * The app's answer to a failure Core caught: show it, record it, or offer [AppError.retry].
 * Register one like any other app service, `service(AppErrorHandler::class, handler)`, in the app's
 * `app { }` block or in a module.
 *
 * ```kotlin
 * app {
 *     service(AppErrorHandler::class, AppErrorHandler { error -> failures += error })
 * }
 * ```
 *
 * It is called once per failure, on the thread that caught it: the frame thread for
 * [AppErrorSource.System] and [AppErrorSource.SceneSwitch], the thread the coroutine ran on for
 * [AppErrorSource.Coroutine]. Keep it short: record the error in the app's state and let the UI
 * show it. If it throws, the throw is logged and the app goes on.
 *
 * With no handler registered, nothing changes: a failure is logged and thrown, or logged and the
 * app carries on, as it did before this existed.
 */
fun interface AppErrorHandler {
    /** Receives [error]. */
    fun onError(error: AppError)
}

/**
 * Hands a failure to the [AppErrorHandler] registered in this lookup.
 *
 * @param source Where it failed.
 * @param cause What was thrown.
 * @param retry Runs the failed step again; see [AppError.retry].
 * @param subject What failed; see [AppError.subject].
 * @return Whether a handler took it. False when none is registered, and the caller then does what it
 * did before this existed: log, rethrow or carry on.
 */
fun AppServiceLookup.reportError(
    source: AppErrorSource,
    cause: Throwable,
    retry: (() -> Unit)? = null,
    subject: String? = null,
): Boolean {
    val handler = service(AppErrorHandler::class) ?: return false
    handler.deliver(AppError(source, cause, retry, subject))
    return true
}

// TooGenericExceptionCaught: a handler that throws must not take down the loop it was told about.
@Suppress("TooGenericExceptionCaught")
internal fun AppErrorHandler.deliver(error: AppError) {
    try {
        onError(error)
    } catch (failure: Exception) {
        log.error(failure) { "The app's error handler failed on $error: ${failure.message}" }
    }
}

private val log = Logger("app-errors")
