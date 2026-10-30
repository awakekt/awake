/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.error

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * A coroutine scope for the app's own background work, whose uncaught failures go to the app's
 * [AppErrorHandler] as [AppErrorSource.Coroutine] instead of reaching the process or the browser's
 * console. It runs on a [SupervisorJob], so one child's failure cancels neither its siblings nor the
 * scope.
 *
 * Register it as a service and the app closes it, cancelling its children, when the app is disposed:
 *
 * ```kotlin
 * app {
 *     service(AppCoroutineScope::class, AppCoroutineScope(serviceLookup()))
 * }
 * ```
 *
 * The handler is looked up when a failure happens, so it may be registered after the scope.
 *
 * With no [AppErrorHandler] registered, a failure is logged and rethrown from the scope's exception
 * handler, so it reaches the thread's uncaught-exception hook as it would from a scope with no handler.
 * A cancellation is never a failure.
 *
 * @param services The app's services, where the handler is looked up.
 * @param context Added to the scope's job and exception handler; `Dispatchers.Default` unless given.
 */
class AppCoroutineScope(
    private val services: AppServiceLookup,
    context: CoroutineContext = Dispatchers.Default,
) : CoroutineScope,
    AutoCloseable {
    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext =
        context + job + CoroutineExceptionHandler { _, failure -> route(failure) }

    /** Cancels the scope's coroutines. Closing twice is harmless. */
    override fun close() {
        job.cancel()
    }

    private fun route(failure: Throwable) {
        if (failure is CancellationException) return
        if (services.reportError(AppErrorSource.Coroutine, failure)) return
        log.error(failure) { "Uncaught exception in an app coroutine: ${failure.message}" }
        throw failure
    }
}

/**
 * Launches [step] in this scope as an app step that can be run again, for work the app starts
 * itself, such as reading the content it needs before its first frame. When it throws, the failure
 * goes to [errors] as an [AppError] of [source] whose [AppError.retry] launches [step] again in this
 * scope; [onDone] receives the result of the first attempt that succeeds. A cancellation is not a
 * failure.
 *
 * With [errors] null, a failure propagates out of the launched coroutine to the scope's exception
 * handling, as from a plain `launch`.
 *
 * ```kotlin
 * scope.launchStep(AppErrorSource.ProjectLoad, errors, subject = "world", onDone = ::play) {
 *     loadProject(files, physicsWorld = ::createJoltPhysicsWorld)
 * }
 * ```
 *
 * @receiver The scope the step, and every retry of it, runs in.
 * @param T What the step returns.
 * @param source What the step is: [AppErrorSource.ProjectLoad] for a project, else [AppErrorSource.Bootstrap].
 * @param errors The app's handler, such as `app.service<AppErrorHandler>()`, or null for none.
 * @param subject What the step loads, for the handler's message.
 * @param onDone Called with the step's result, on the scope's thread.
 * @param step The work, run again by [AppError.retry].
 * @return The job of the first attempt.
 */
fun <T> CoroutineScope.launchStep(
    source: AppErrorSource,
    errors: AppErrorHandler?,
    subject: String? = null,
    onDone: (T) -> Unit = {},
    step: suspend () -> T,
): Job = launch { attempt(this@launchStep, source, errors, subject, onDone, step) }

// TooGenericExceptionCaught: whatever an app's step throws must reach the handler, not the process.
@Suppress("TooGenericExceptionCaught", "LongParameterList")
private suspend fun <T> attempt(
    scope: CoroutineScope,
    source: AppErrorSource,
    errors: AppErrorHandler?,
    subject: String?,
    onDone: (T) -> Unit,
    step: suspend () -> T,
) {
    val result = try {
        step()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        if (errors == null) throw failure
        log.error(failure) { "App step failed (${subject ?: source}): ${failure.message}" }
        // Launched in the app's scope, not in this attempt's own coroutine, which has completed by then.
        val again: () -> Unit = { scope.launch { attempt(scope, source, errors, subject, onDone, step) } }
        errors.deliver(AppError(source, failure, retry = again, subject = subject))
        return
    }
    onDone(result)
}

private val log = Logger("app-errors")
