/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.camera

import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.input.KeybindingProfile
import com.awakekt.awake.scene.controls.input.keybindingProfile

/**
 * Predicate resolving whether an input frame constitutes an active camera drag.
 */
fun interface CameraDragPredicate {
    fun isDragging(input: GameplayInput): Boolean
}

/**
 * Configurable policy controlling which buttons and gestures activate camera navigation.
 *
 * A pan drag wins over an orbit drag when both hold.
 */
data class CameraGesturePolicy(
    val isOrbitDragging: CameraDragPredicate = CameraDragPredicate { input ->
        input.pointerDown || input.isDown(PointerButton.Secondary)
    },
    /** Moves the orbit point, or a free-fly eye, across the view. Off by default: a follow camera must stay on its target. */
    val isPanDragging: CameraDragPredicate = CameraDragPredicate { false },
    /** Whether [flyKeys] move a [CameraMode.FreeFly] camera this frame. */
    val canFly: CameraDragPredicate = CameraDragPredicate { true },
    val flyKeys: KeybindingProfile<CameraFlyAction> = defaultFlyKeys(),
    val lookSensitivity: Float = 0.005f,
    /** Distance each scroll notch zooms by. */
    val zoomSensitivity: Float = 0.5f,
    /** Share of the current distance each scroll notch adds to [zoomSensitivity], so zoom keeps pace far out. */
    val zoomProportion: Float = 0f,
    /** World units a pan moves per pixel, per unit of orbit distance. */
    val panSensitivity: Float = 0.0015f,
    val invertYaw: Boolean = false,
    val invertPitch: Boolean = false,
) {
    companion object {
        /** Default game navigation: Left drag or Right drag orbit. */
        val Default = CameraGesturePolicy()

        /**
         * Editor navigation: Right, Alt + Left or plain Left drag orbits, Middle drag pans, zoom
         * keeps pace with distance, and free-fly moves only while Right is held.
         */
        val Editor = CameraGesturePolicy(
            isOrbitDragging = CameraDragPredicate { input ->
                input.isDown(PointerButton.Secondary) || (input.pointerDown && input.isDown(Key.Alt)) || input.pointerDown
            },
            isPanDragging = CameraDragPredicate { input -> input.isDown(PointerButton.Middle) },
            canFly = CameraDragPredicate { input -> input.isDown(PointerButton.Secondary) },
            zoomProportion = 0.1f,
        )

        /** W/S/A/D along the view, Q/E down/up, Shift to go faster. */
        fun defaultFlyKeys(): KeybindingProfile<CameraFlyAction> = keybindingProfile {
            bind(CameraFlyAction.Forward, Key.W)
            bind(CameraFlyAction.Back, Key.S)
            bind(CameraFlyAction.Left, Key.A)
            bind(CameraFlyAction.Right, Key.D)
            bind(CameraFlyAction.Down, Key.Q)
            bind(CameraFlyAction.Up, Key.E)
            bind(CameraFlyAction.Fast, Key.Shift)
        }
    }
}

/** A free-fly movement bound in [CameraGesturePolicy.flyKeys]. */
enum class CameraFlyAction { Forward, Back, Left, Right, Down, Up, Fast }
