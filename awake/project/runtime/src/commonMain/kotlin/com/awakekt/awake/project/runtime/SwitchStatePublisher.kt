/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The [SceneSwitchState] of [ProjectSceneRunner]'s requests. Everything but [report] runs on the frame
 * thread, so the state only changes at the safe points the runner already has; [report] is for the
 * loading coroutine, whose progress is queued here and applied by [applyProgress].
 */
internal class SwitchStatePublisher {
    private val current = MutableStateFlow<SceneSwitchState>(SceneSwitchState.Idle)

    /** What the app's `SceneSwitchStatus` service shows. */
    val state: StateFlow<SceneSwitchState> = current.asStateFlow()

    // The latest progress of the load in flight, conflated: only the newest matters, and a number says whose.
    private val progress = Channel<LoadProgress>(Channel.CONFLATED)

    // Whether the load in flight waits for its content's gate, in order: each request waits at most once.
    private val waits = Channel<Wait>(Channel.UNLIMITED)

    // The scene a switch has put in, until the frame that first played it is over; see frameStarted.
    private var readyPath: String? = null
    private var readyArmed = false

    /** A request for [path] has started. */
    fun requested(path: String) {
        readyPath = null
        current.value = SceneSwitchState.Loading(path, 0f)
    }

    /** From the loading coroutine: request [number] has loaded [share] of its bytes. */
    fun report(number: Int, share: Float) {
        progress.trySend(LoadProgress(number, share))
    }

    /** From the loading coroutine: request [number] waits for its content's gate to open, or has stopped waiting. */
    fun reportWaiting(number: Int, path: String, waiting: Boolean) {
        waits.trySend(Wait(number, path, waiting))
    }

    /** Publishes what [report] and [reportWaiting] queued, if it is for request [requestNumber], the one loading. */
    fun applyProgress(requestNumber: Int) {
        var wait = waits.tryReceive().getOrNull()
        while (wait != null) {
            if (wait.number == requestNumber) {
                current.value = if (wait.waiting) SceneSwitchState.WaitingForContent(wait.path) else SceneSwitchState.Loading(wait.path, 0f)
            }
            wait = waits.tryReceive().getOrNull()
        }
        var update = progress.tryReceive().getOrNull()
        while (update != null) {
            val loading = current.value as? SceneSwitchState.Loading
            if (loading != null && update.number == requestNumber && update.share > loading.progress) {
                current.value = SceneSwitchState.Loading(loading.path, update.share)
            }
            update = progress.tryReceive().getOrNull()
        }
    }

    /** The scene at [path] has loaded, all of it, and is about to be put in. */
    fun loaded(path: String) {
        current.value = SceneSwitchState.Loading(path, 1f)
    }

    /** The scene at [path] is in place; it is ready once the frame it first plays in is over. */
    fun swapped(path: String) {
        readyPath = path
        readyArmed = false
    }

    /** The scene at [path] can't be played, for [cause]. */
    fun failed(path: String, cause: Throwable) {
        current.value = SceneSwitchState.Failed(path, cause)
    }

    /** At the start of a frame: the new scene's first frame, if it had one, is over, so it is ready. */
    fun frameStarted() {
        val path = readyPath
        if (path == null || !readyArmed) return
        readyPath = null
        readyArmed = false
        current.value = SceneSwitchState.Ready(path)
    }

    /** At the end of a frame: a scene put in during it, or before its systems ran, has had its first frame. */
    fun frameEnded() {
        if (readyPath != null) readyArmed = true
    }

    /** The project has stopped: nothing is loading or about to be ready. */
    fun stopped() {
        readyPath = null
        progress.close()
        waits.close()
        current.value = SceneSwitchState.Idle
    }

    private class LoadProgress(val number: Int, val share: Float)

    private class Wait(val number: Int, val path: String, val waiting: Boolean)
}
