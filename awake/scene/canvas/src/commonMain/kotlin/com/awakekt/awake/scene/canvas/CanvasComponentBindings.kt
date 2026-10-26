/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.scene.binding.SceneBindingHooks
import com.awakekt.awake.scene.binding.SceneValueConverter
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.ui.canvas.CanvasStepper
import com.awakekt.awake.ui.canvas.CanvasStepperWidget

// The converter and hooks used by the canvas bindings generated into CanvasSceneComponents.
// Whole-value components use @SceneBinding(whole = ...); only the legacy CanvasElementBinding is hand-written.

/** Converts a saved [SceneVec3] to a live [Vec3f] and back, as a follower's offset is saved. */
internal object CanvasVec3Converter : SceneValueConverter<SceneVec3, Vec3f> {
    override fun toLive(saved: SceneVec3): Vec3f = Vec3f(saved.x, saved.y, saved.z)

    override fun toSaved(live: Vec3f): SceneVec3 = SceneVec3(live.x, live.y, live.z)
}

/**
 * Saves a stepper's live number as where it starts. Loading needs nothing more: setting a stepper's
 * settings puts its number at their value.
 */
internal object CanvasStepperStart : SceneBindingHooks<CanvasStepperWidget, SceneCanvasStepper> {
    override fun afterCopyToSaved(live: CanvasStepperWidget, saved: SceneCanvasStepper): SceneCanvasStepper {
        val settings = live.settings
        val starts = settings != null || live.number != CanvasStepper.Default.value
        return SceneCanvasStepper(if (starts) (settings ?: CanvasStepper.Default).copy(value = live.number) else null)
    }
}
