/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.state

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.core.state.ReducerStore
import com.awakekt.awake.core.state.reducerStore
import com.awakekt.awake.studio.fixture.StudioSceneRegistry

internal class StudioStore(
    initialState: StudioContract.State = StudioContract.State(),
    private val delegate: ReducerStore<StudioContract.State, StudioContract.Intent, StudioContract.Effect> =
        reducerStore(initialState) { state: StudioContract.State, intent: StudioContract.Intent ->
            when (intent) {
                is StudioContract.Intent.SetCameraMode -> {
                    log.debug { "Set camera mode: ${intent.mode}" }
                    state.copy(camera = state.camera.copy(mode = intent.mode)) to null
                }

                is StudioContract.Intent.SetProjection -> {
                    log.debug { "Set projection: ${intent.projection}" }
                    state.copy(camera = state.camera.copy(projection = intent.projection)) to null
                }

                StudioContract.Intent.AlignViewToCamera -> {
                    log.info { "Align view to camera" }
                    state to StudioContract.Effect.AlignViewToCamera
                }

                StudioContract.Intent.SaveScene -> {
                    log.info { "Save scene requested" }
                    state to StudioContract.Effect.SaveScene
                }

                is StudioContract.Intent.SceneSaved -> {
                    log.info { "Scene saved successfully to: ${intent.location}" }
                    state.copy(lastSavedTo = intent.location) to null
                }

                is StudioContract.Intent.SelectDockTab -> {
                    log.debug { "Switched dock tab to: ${intent.id}" }
                    state.copy(dockTab = intent.id) to null
                }

                is StudioContract.Intent.SelectFile -> {
                    log.debug { "Selected file: ${intent.path}" }
                    state.copy(selectedFile = intent.path) to null
                }

                is StudioContract.Intent.SelectScene -> {
                    val descriptor = StudioSceneRegistry.findById(intent.sceneId)
                    if (descriptor != null) {
                        log.info { "Loading scene '${descriptor.title}' (${intent.sceneId})" }
                        state.copy(activeSceneId = intent.sceneId) to StudioContract.Effect.LoadScene(descriptor)
                    } else {
                        log.warn { "Unknown scene ID: ${intent.sceneId}" }
                        state to null
                    }
                }

                StudioContract.Intent.StartPlay -> {
                    log.info { "Entering play mode (simulation started)" }
                    state to StudioContract.Effect.StartPlay
                }

                StudioContract.Intent.StopPlay -> {
                    log.info { "Exiting play mode (editing active)" }
                    state to StudioContract.Effect.StopPlay
                }

                StudioContract.Intent.ReloadFixture -> {
                    log.info { "Reloading default scene fixture" }
                    state to StudioContract.Effect.ReloadFixture
                }
            }
        },
) : ReducerStore<StudioContract.State, StudioContract.Intent, StudioContract.Effect> by delegate {

    private companion object {
        val log = Logger("studio.state")
    }

    fun startPlay() {
        dispatch(StudioContract.Intent.StartPlay)
    }

    fun stopPlay() {
        dispatch(StudioContract.Intent.StopPlay)
    }

    fun reloadFixture() {
        dispatch(StudioContract.Intent.ReloadFixture)
    }

    fun selectScene(sceneId: String) {
        dispatch(StudioContract.Intent.SelectScene(sceneId))
    }
}
