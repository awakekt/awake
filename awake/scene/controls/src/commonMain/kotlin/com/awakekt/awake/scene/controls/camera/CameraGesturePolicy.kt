/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.camera

import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.scene.controls.GameplayInput

/**
 * Predicate resolving whether an input frame constitutes an active camera drag.
 */
fun interface CameraDragPredicate {
    fun isDragging(input: GameplayInput): Boolean
}

/**
 * Configurable policy controlling which buttons and gestures activate camera navigation.
 */
data class CameraGesturePolicy(
    val isOrbitDragging: CameraDragPredicate = CameraDragPredicate { input ->
        input.pointerDown || input.isDown(PointerButton.Secondary)
    },
    val isPanDragging: CameraDragPredicate = CameraDragPredicate { input ->
        input.isDown(PointerButton.Middle) || (input.pointerDown && input.isDown(Key.Shift))
    },
    val lookSensitivity: Float = 0.005f,
    val zoomSensitivity: Float = 0.5f,
    val invertYaw: Boolean = false,
    val invertPitch: Boolean = false,
) {
    companion object {
        /** Default game navigation: Left drag or Right drag orbit. */
        val Default = CameraGesturePolicy()

        /** Editor navigation: Right drag orbit, Alt + Left drag orbit, or plain Left drag. */
        val Editor = CameraGesturePolicy(
            isOrbitDragging = CameraDragPredicate { input ->
                input.isDown(PointerButton.Secondary) || (input.pointerDown && input.isDown(Key.Alt)) || input.pointerDown
            },
            isPanDragging = CameraDragPredicate { input ->
                input.isDown(PointerButton.Middle) || (input.isDown(Key.Alt) && input.isDown(PointerButton.Middle))
            },
        )
    }
}
