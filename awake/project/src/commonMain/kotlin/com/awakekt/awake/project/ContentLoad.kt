/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

/**
 * Where staging a project's content bytes is, for a loading screen or a server's log: what
 * [ContentStages] reports while it fetches a stage ahead of play. It describes bytes only; a scene
 * swap is described by the runtime's scene switch state, so an app that shows both reads both.
 *
 * ```
 * Idle -> Loading(stage, 0, total) -> ... -> Ready(stage)
 *              |                   \-> Failed(stage, cause) or TooNew(stage, cause)
 *              \-> WaitingForContent(stage) -> Loading(stage, ...) -> ...
 * ```
 */
sealed interface ContentLoad {
    /** Nothing has been staged yet. */
    data object Idle : ContentLoad

    /**
     * The files of [stage] are being fetched: [bytesDone] of [bytesTotal] have arrived. [bytesTotal] is 0
     * when the content source can't count bytes, as local files can't; the stage then goes from
     * `Loading(stage, 0, 0)` straight to [Ready].
     */
    data class Loading(val stage: String, val bytesDone: Long, val bytesTotal: Long) : ContentLoad {
        /** [bytesDone] over [bytesTotal], from 0 to 1; 0 while the total is unknown. */
        val fraction: Float get() = if (bytesTotal <= 0L) 0f else (bytesDone.toDouble() / bytesTotal).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Some files of [stage] can't be read until the app opens a gate, such as attaching a signed-in
     * player's protected content to a [LayeredAssetSource]. Staging goes on as soon as it opens.
     */
    data class WaitingForContent(val stage: String) : ContentLoad

    /** Every file of [stage] that exists has been fetched. */
    data class Ready(val stage: String) : ContentLoad

    /**
     * [stage] failed because its content is newer than this build reads ([com.awakekt.awake.core.io.isTooNew]
     * holds for [cause]): the player needs an update, and retrying fails the same way.
     */
    data class TooNew(val stage: String, val cause: Throwable) : ContentLoad

    /** [stage] failed for [cause], such as a network error or a file that fails its check; a retry may succeed. */
    data class Failed(val stage: String, val cause: Throwable) : ContentLoad
}
