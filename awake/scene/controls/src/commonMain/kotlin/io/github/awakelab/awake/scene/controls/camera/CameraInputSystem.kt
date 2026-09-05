/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.controls.camera

import io.github.awakelab.awake.core.input.Key
import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.controls.GameplayInput
import io.github.awakelab.awake.scene.controls.camera.ActiveCamera
import io.github.awakelab.awake.scene.controls.camera.CameraInputSystem.Companion.DEFAULT_MODE_KEYS
import io.github.awakelab.awake.scene.controls.camera.CameraMode
import io.github.awakelab.awake.scene.controls.camera.CameraRig

/**
 * Switches the [CameraMode] on the [ActiveCamera] via hotkeys, so only one mode consumes
 * input and drives movement at a time.
 *
 * [modeKeys] is injectable because a hotkey is a whole-application decision, not something a
 * reusable system should claim unilaterally -- [DEFAULT_MODE_KEYS] deliberately skips
 * [Key.F3], which `AppUiRuntime` already owns for the debug overlay.
 */
class CameraInputSystem(
    /** This frame's input, with whatever the UI claimed already taken out. */
    private val inputProvider: () -> GameplayInput,
    private val modeKeys: Map<Key, CameraMode> = DEFAULT_MODE_KEYS,
) : System {
    override fun update(world: World, delta: Float) {
        // The edges come from InputSnapshot, which recomputes them every frame regardless of
        // this early return -- so releasing the UI can't replay a keypress made over a widget.
        val input = inputProvider()
        for ((key, mode) in modeKeys) {
            if (input.wasPressed(key)) setCameraMode(world, mode)
        }
    }

    private fun setCameraMode(world: World, mode: CameraMode) {
        world.queryEach(CameraRig::class, ActiveCamera::class) { _, config, _ ->
            config.mode = mode
        }
    }

    companion object {
        /** F3 is intentionally absent -- it is the engine's debug-overlay toggle. */
        val DEFAULT_MODE_KEYS: Map<Key, CameraMode> = mapOf(
            Key.F1 to CameraMode.FirstPerson,
            Key.F2 to CameraMode.ThirdPerson,
            Key.F4 to CameraMode.Cinematic,
            Key.F5 to CameraMode.TopDown,
        )
    }
}
