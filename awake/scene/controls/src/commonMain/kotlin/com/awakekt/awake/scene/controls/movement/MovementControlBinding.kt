/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.movement

import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.controls.ControlsSceneComponents

/**
 * Registers the controls scene components (`movement_control`, `camera_rig` and `input_actions`) with this registry.
 *
 * @return This [SceneComponentRegistry] instance for chaining.
 */
fun SceneComponentRegistry.registerControls(): SceneComponentRegistry = register(ControlsSceneComponents)
