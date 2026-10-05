/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring.infrastructure

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.camera.CameraInputSystem
import com.awakekt.awake.scene.controls.camera.CameraSystem
import com.awakekt.awake.scene.controls.movement.MatrixRelativeMovementSystem
import com.awakekt.awake.scene.controls.movement.PlayerInputSystem
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneSystemHandle

/**
 * What gameplay may read of the input right now: the keyboard, pointer and touch state, minus
 * whatever the UI has claimed. The input every control system here reads, in one place, so a system
 * a sample or a game writes asks for the same thing.
 *
 * @return The current filtered [GameplayInput] snapshot.
 */
fun SceneAppLifecycleRuntime.gameplayInput(): GameplayInput =
    GameplayInput(requireService(Input::class).currentSnapshot, uiOwnership)

/**
 * Registers a standard [CameraSystem] into this scene application DSL.
 *
 * @param name Unique registration name for the system.
 * @return Handle to the registered [CameraSystem].
 */
fun SceneAppDsl.cameraSystem(
    name: String = "camera",
): SceneSystemHandle<CameraSystem> = frameSystem(name) {
    CameraSystem(
        inputProvider = { gameplayInput() },
    )
}

/**
 * Registers a standard [CameraInputSystem] into this scene application DSL.
 *
 * @param name Unique registration name for the system.
 * @return Handle to the registered [CameraInputSystem].
 */
fun SceneAppDsl.cameraInputSystem(
    name: String = "cameraInput",
): SceneSystemHandle<CameraInputSystem> = frameSystem(name) {
    CameraInputSystem(
        inputProvider = { gameplayInput() },
    )
}

/**
 * Registers a standard [PlayerInputSystem] into this scene application DSL.
 *
 * @param name Unique registration name for the system.
 * @return Handle to the registered [PlayerInputSystem].
 */
fun SceneAppDsl.playerInputSystem(
    name: String = "playerInput",
): SceneSystemHandle<PlayerInputSystem> = frameSystem(name) {
    PlayerInputSystem(
        inputProvider = { gameplayInput() },
    )
}

/**
 * Registers a standard [MatrixRelativeMovementSystem] into this scene application DSL.
 *
 * @param name Unique registration name for the system.
 * @param speed Movement speed scalar in units per second.
 * @return Handle to the registered [MatrixRelativeMovementSystem].
 */
fun SceneAppDsl.matrixRelativeMovementSystem(
    name: String = "movement",
    speed: Float = 5f,
): SceneSystemHandle<MatrixRelativeMovementSystem> = frameSystem(name) {
    MatrixRelativeMovementSystem(speed = speed)
}
