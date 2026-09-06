/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.debug

import com.awakekt.awake.ecs.World

/**
 * This scene's [WorldDebugSettings], created on first use.
 *
 * The missing half of the debug overlay. [DebugVisualizationSystem] returns immediately when no
 * settings entity exists, and nothing created one -- so a scene that had not been authored with
 * the component could not draw bounds, a frustum or a light gizmo no matter which flag it set,
 * and the flags looked unwired when they were merely unreachable.
 *
 * Get-or-create rather than a required scene node: which diagnostics are on is a decision made
 * while looking at the running frame, not something a scene document should have to declare in
 * advance.
 */
fun World.debugSettings(): WorldDebugSettings = debugSettingsOrNull() ?: run {
    val settings = WorldDebugSettings()
    add(create(), settings)
    settings
}

/** This scene's settings, or null when nothing has asked for any yet. */
fun World.debugSettingsOrNull(): WorldDebugSettings? {
    var found: WorldDebugSettings? = null
    queryEach<WorldDebugSettings> { _, settings ->
        if (found == null) found = settings
    }
    return found
}
