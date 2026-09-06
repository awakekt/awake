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
import com.awakekt.awake.scene.runtime.SceneSystemHandle

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
