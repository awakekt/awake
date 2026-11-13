/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.scene.runtime.PlannedSystem

/**
 * What the scene a [runProject] app is playing planned: its systems by name and phase, in the order they
 * run (fixed, then pre-animation, then frame), after any the host disabled. Read it as an app service for
 * diagnostics and tests. The scene app's own `plannedSystems` lists only the three project systems that
 * `runProject` registers, which run these.
 */
class ProjectSystemPlan internal constructor(private val read: () -> List<PlannedSystem>) {
    /** The playing scene's planned systems; empty while a scene switch has none playing. */
    val planned: List<PlannedSystem> get() = read()
}
