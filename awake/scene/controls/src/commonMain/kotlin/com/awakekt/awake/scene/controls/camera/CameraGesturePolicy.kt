/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.camera

import com.awakekt.awake.core.input.AxisAction
import com.awakekt.awake.core.input.ButtonAction
import com.awakekt.awake.core.input.InputActionDefinition
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.input.KeybindingProfile
import com.awakekt.awake.scene.controls.input.keybindingProfile

/**
 * Predicate resolving whether an input frame constitutes an active camera drag.
 */
fun interface CameraDragPredicate {
    /**
     * Evaluates whether the given gameplay input satisfies the drag condition.
     *
     * @param input Current frame gameplay input state.
     * @return `true` if dragging is active, `false` otherwise.
     */
    fun isDragging(input: GameplayInput): Boolean
}

/**
 * Configurable policy controlling which buttons and gestures activate camera navigation.
 *
 * A pan drag wins over an orbit drag when both hold.
 *
 * @property isOrbitDragging Predicate resolving whether an orbit drag is currently active.
 * @property isPanDragging Predicate resolving whether a pan drag is currently active.
 * @property canFly Predicate resolving whether keyboard free-fly navigation is permitted this frame.
 * @property flyKeys Keybinding mapping for directional free-fly navigation actions. Deprecated: pass [flyActions].
 * @property lookSensitivity Sensitivity scale applied to pointer movement during look and orbit.
 * @property zoomSensitivity Base distance units traversed per scroll wheel step.
 * @property zoomProportion Proportional distance scaling factor added per scroll notch to maintain zoom feel at distance.
 * @property panSensitivity Pan distance factor in world units per pixel per unit of orbit distance.
 * @property invertYaw Whether to invert horizontal yaw rotation input.
 * @property invertPitch Whether to invert vertical pitch rotation input.
 * @property flyActions The input actions that move a [CameraMode.FreeFly] camera, named as in
 *   [CameraFlyActions]. By default [flyKeys]' bindings, which are [CameraFlyActions.defaults] unless
 *   given.
 */
@Suppress("DEPRECATION") // flyKeys stays honoured, through flyActions' default, until it is removed.
data class CameraGesturePolicy(
    val isOrbitDragging: CameraDragPredicate = CameraDragPredicate { input ->
        input.pointerDown || input.isDown(PointerButton.Secondary)
    },
    /** Moves the orbit point, or a free-fly eye, across the view. Off by default: a follow camera must stay on its target. */
    val isPanDragging: CameraDragPredicate = CameraDragPredicate { false },
    /** Whether [flyActions] move a [CameraMode.FreeFly] camera this frame. */
    val canFly: CameraDragPredicate = CameraDragPredicate { true },
    @Deprecated("Bind the fly actions instead", ReplaceWith("flyActions"))
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
    val flyActions: List<InputActionDefinition> = flyKeys.toFlyActions(),
) {
    /**
     * Preset camera gesture policies and default configurations.
     */
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
        @Deprecated("The fly actions' defaults", ReplaceWith("CameraFlyActions.defaults"))
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

/** [flyKeys]' bindings as the fly actions, for a policy given keys rather than actions. */
@Suppress("DEPRECATION")
private fun KeybindingProfile<CameraFlyAction>.toFlyActions(): List<InputActionDefinition> {
    fun keys(action: CameraFlyAction): Set<Key> = getBinding(action)?.let { setOfNotNull(it.primary, it.secondary) }.orEmpty()
    return listOf(
        AxisAction(
            CameraFlyActions.FLY,
            up = keys(CameraFlyAction.Forward),
            down = keys(CameraFlyAction.Back),
            left = keys(CameraFlyAction.Left),
            right = keys(CameraFlyAction.Right),
        ),
        AxisAction(CameraFlyActions.RISE, up = keys(CameraFlyAction.Up), down = keys(CameraFlyAction.Down)),
        ButtonAction(CameraFlyActions.FAST, keys = keys(CameraFlyAction.Fast)),
    )
}

/**
 * Directional movement actions for free-fly camera navigation.
 */
@Deprecated("The fly actions, named in CameraFlyActions", ReplaceWith("CameraFlyActions"))
enum class CameraFlyAction {
    /** Translate forward along the view direction. */
    Forward,

    /** Translate backward along the view direction. */
    Back,

    /** Translate left along the camera right vector. */
    Left,

    /** Translate right along the camera right vector. */
    Right,

    /** Translate vertically downward in world space. */
    Down,

    /** Translate vertically upward in world space. */
    Up,

    /** Accelerate translation speed while held. */
    Fast,
}
