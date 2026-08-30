/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.ecs.System

class SceneSystemHandle<T : System>(
    val name: String,
)

class SceneSystemRegistration(
    val handle: SceneSystemHandle<out System>,
    val phase: SceneSystemPhase,
    val factory: SceneSystemFactory,
)
