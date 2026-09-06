/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

/**
 * Scene-runtime schedule bucket for an ECS [com.awakekt.awake.ecs.System].
 *
 * The ECS core deliberately does not own scheduling policy. A system describes what to do;
 * the scene runtime decides when that work runs.
 */
enum class SceneSystemPhase {
    /** Run during fixed-timestep simulation steps. Use for deterministic gameplay/physics. */
    Fixed,

    /** Run once for every rendered frame. Use for cameras, transforms, rendering, and UI-adjacent drivers. */
    Frame,
}
