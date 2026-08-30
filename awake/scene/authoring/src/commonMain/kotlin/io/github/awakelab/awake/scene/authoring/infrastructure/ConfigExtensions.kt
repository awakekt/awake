/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.authoring.infrastructure

import io.github.awakelab.awake.core.input.Input
import io.github.awakelab.awake.scene.authoring.SceneAppDsl
import io.github.awakelab.awake.scene.controls.GameplayInput
import io.github.awakelab.awake.scene.controls.systems.CameraInputSystem
import io.github.awakelab.awake.scene.controls.systems.CameraSystem
import io.github.awakelab.awake.scene.controls.systems.MatrixRelativeMovementSystem
import io.github.awakelab.awake.scene.controls.systems.PlayerInputSystem
import io.github.awakelab.awake.scene.runtime.SceneSystemHandle

fun SceneAppDsl.cameraSystem(
    name: String = "camera",
): SceneSystemHandle<CameraSystem> = frameSystem(name) {
    CameraSystem(
        inputProvider = { GameplayInput(requireService(Input::class).currentSnapshot, uiOwnership) },
    )
}

fun SceneAppDsl.cameraInputSystem(
    name: String = "cameraInput",
): SceneSystemHandle<CameraInputSystem> = frameSystem(name) {
    CameraInputSystem(
        inputProvider = { GameplayInput(requireService(Input::class).currentSnapshot, uiOwnership) },
    )
}

fun SceneAppDsl.playerInputSystem(
    name: String = "playerInput",
): SceneSystemHandle<PlayerInputSystem> = frameSystem(name) {
    PlayerInputSystem(
        inputProvider = { GameplayInput(requireService(Input::class).currentSnapshot, uiOwnership) },
    )
}

fun SceneAppDsl.matrixRelativeMovementSystem(
    name: String = "movement",
    speed: Float = 5f,
): SceneSystemHandle<MatrixRelativeMovementSystem> = frameSystem(name) {
    MatrixRelativeMovementSystem(speed = speed)
}
