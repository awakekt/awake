/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

/** What a loading screen is showing, so an app picks its own localized text for each. */
enum class LoadingPhase {
    /** Nothing is loading. */
    Idle,

    /** Bytes are arriving. */
    Loading,

    /** Waiting for the app to open a gate, such as a sign-in. */
    WaitingForContent,

    /** Done. */
    Ready,

    /** The content is newer than this build: the player needs an update. */
    TooNew,

    /** Loading failed; a retry may succeed. */
    Failed,
}

/**
 * The default model of a loading screen, made from a [ContentLoad] with [loadingView]: everything a
 * bar, a label and a retry button need, with no text of its own. The app turns [phase] and [stage] into
 * words in its own language, and draws [fraction] with whatever progress component it uses.
 *
 * @property phase What the screen is showing.
 * @property stage The stage being loaded, or null when [phase] is [LoadingPhase.Idle].
 * @property fraction How far the stage has come, from 0 to 1; null while it can't be measured, for an
 * indeterminate bar: waiting, or loading through a source that doesn't count bytes.
 * @property error Why loading stopped, for [LoadingPhase.TooNew] and [LoadingPhase.Failed]; null otherwise.
 * @property canRetry Whether a retry button makes sense: true for [LoadingPhase.Failed] only, since content
 * that is too new fails the same way again.
 */
data class LoadingView(
    val phase: LoadingPhase,
    val stage: String?,
    val fraction: Float?,
    val error: Throwable?,
    val canRetry: Boolean,
)

/** This state as a [LoadingView] for a loading screen. */
fun ContentLoad.loadingView(): LoadingView = when (this) {
    ContentLoad.Idle -> LoadingView(LoadingPhase.Idle, null, null, null, canRetry = false)
    is ContentLoad.Loading -> LoadingView(LoadingPhase.Loading, stage, if (bytesTotal > 0L) fraction else null, null, canRetry = false)
    is ContentLoad.WaitingForContent -> LoadingView(LoadingPhase.WaitingForContent, stage, null, null, canRetry = false)
    is ContentLoad.Ready -> LoadingView(LoadingPhase.Ready, stage, 1f, null, canRetry = false)
    is ContentLoad.TooNew -> LoadingView(LoadingPhase.TooNew, stage, null, cause, canRetry = false)
    is ContentLoad.Failed -> LoadingView(LoadingPhase.Failed, stage, null, cause, canRetry = true)
}
