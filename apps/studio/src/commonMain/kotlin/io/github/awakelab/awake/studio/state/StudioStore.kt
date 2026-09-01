/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.state

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class StudioStore {
    private val _state = MutableStateFlow(StudioContract.State())
    val state: StateFlow<StudioContract.State> = _state.asStateFlow()

    private val effects = Channel<StudioContract.Effect>(Channel.BUFFERED)

    fun dispatch(intent: StudioContract.Intent) {
        when (intent) {
            is StudioContract.Intent.SetCameraMode -> {
                _state.update { it.copy(camera = it.camera.copy(mode = intent.mode)) }
            }

            is StudioContract.Intent.SetProjection -> {
                _state.update { it.copy(camera = it.camera.copy(projection = intent.projection)) }
            }

            StudioContract.Intent.AlignViewToCamera -> {
                effects.trySend(StudioContract.Effect.AlignViewToCamera)
            }

            StudioContract.Intent.SaveScene -> {
                effects.trySend(StudioContract.Effect.SaveScene)
            }

            is StudioContract.Intent.SceneSaved -> {
                _state.update { it.copy(lastSavedTo = intent.location) }
            }

            is StudioContract.Intent.SelectDockTab -> {
                _state.update { it.copy(dockTab = intent.id) }
            }

            is StudioContract.Intent.SelectFile -> {
                _state.update { it.copy(selectedFile = intent.path) }
            }

        }
    }

    fun drainEffects(): List<StudioContract.Effect> = buildList {
        while (true) {
            val effect = effects.tryReceive().getOrNull() ?: break
            add(effect)
        }
    }

    fun startPlay() {
        effects.trySend(StudioContract.Effect.StartPlay)
    }

    fun stopPlay() {
        effects.trySend(StudioContract.Effect.StopPlay)
    }

    fun reloadFixture() {
        effects.trySend(StudioContract.Effect.ReloadFixture)
    }
}
