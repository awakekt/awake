/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import kotlinx.coroutines.flow.StateFlow

/**
 * Where a [SceneSwitch] request is, for a game that shows a loading curtain, a progress bar or an
 * error while the next scene comes in. Awake draws none of it: the state is all `awake:project:runtime`
 * exposes, so a played project still needs no display.
 *
 * One request moves through [Loading] to [Ready], or to [Failed]:
 * ```
 * Idle -> Loading(path, 0) -> ... -> Loading(path, 1) -> Ready(path)
 *                          \-> Failed(path, cause)
 * ```
 * Asking for another scene while one loads replaces the target: the state is [Loading] of the new path
 * from 0 again. After [Ready] or [Failed] the state stays until the next request.
 */
sealed interface SceneSwitchState {
    /** No scene has been asked for, or the project has stopped. */
    data object Idle : SceneSwitchState

    /**
     * The scene at [path] is being read, and is swapped in once it has, while the scene being left
     * plays on. It stays [Loading] after the swap too, until the new scene has had its first frame.
     *
     * @property path The requested scene, relative to the project root.
     * @property progress The share of the target scene's content bytes that have arrived, from 0 to 1.
     * It never decreases within one request, and is 1 once the scene has loaded, before [Ready].
     * The bytes counted are those of the scene file and every file it names by project path (a model,
     * an image, a sound, a prefab) that the project's content source can prefetch, which today means an
     * [com.awakekt.awake.project.IndexedAssetSource] with a content cache. A source that can't report
     * bytes, such as local files, wrapped sources or an index with no cache, reports 0 and then 1 with
     * nothing between. Files that are named only inside
     * another file, such as the textures of a model, are not counted ahead; they load before 1 all the
     * same.
     */
    data class Loading(val path: String, val progress: Float) : SceneSwitchState

    /**
     * The scene at [path] is playing and has finished its first frame, so a curtain can lift. That
     * is the start of the frame after the one that first ran its systems and drew it, so on a host
     * with a renderer the scene has been handed to the renderer once; on a host without one it is
     * simply the first completed frame.
     *
     * Pipeline compilation and texture uploads that a backend defers past its first frame are not
     * waited for.
     */
    data class Ready(val path: String) : SceneSwitchState

    /**
     * The scene at [path] couldn't be read, decoded, checked or put in, for [cause]. The scene that
     * was playing goes on exactly as it did, and the failure is also logged once.
     */
    data class Failed(val path: String, val cause: Throwable) : SceneSwitchState
}

/**
 * Observes the [SceneSwitch] of a project played with [runProject]: an app reaches it as the
 * `SceneSwitchStatus` service, and a system through [SceneHostServices.sceneSwitchStatus]. It is a
 * separate service, not a member of [SceneSwitch], so a [SceneSwitch] stays a function to implement.
 *
 * Like `Strings.locale`, [state] is a [StateFlow] that holds its latest value: a frame loop reads
 * `state.value` each frame, and a collector may miss a state that is replaced before it runs. The
 * state changes on the frame thread.
 */
interface SceneSwitchStatus {
    /** Where the latest [SceneSwitch.request] is; [SceneSwitchState.Idle] until one is made. */
    val state: StateFlow<SceneSwitchState>
}
