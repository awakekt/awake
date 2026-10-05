/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.System

/**
 * Type-safe handle identifying a registered ECS [System] within a scene runtime.
 *
 * @param T The specific system type.
 * @property name The descriptive, unique name of the system registration.
 */
class SceneSystemHandle<T : System>(
    val name: String,
)

/**
 * Registration entry binding a system handle, phase, and factory within a scene.
 *
 * @property handle The typed [SceneSystemHandle] identifying the system.
 * @property phase The [SceneSystemPhase] in which the system executes.
 * @property factory Factory lambda instantiating the system.
 */
class SceneSystemRegistration(
    val handle: SceneSystemHandle<out System>,
    val phase: SceneSystemPhase,
    val factory: SceneSystemFactory,
)
