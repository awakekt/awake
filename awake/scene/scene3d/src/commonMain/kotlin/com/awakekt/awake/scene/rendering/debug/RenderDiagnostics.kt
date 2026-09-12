/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.debug

/** Last scene-extraction counters for an in-app diagnostics surface. */
object RenderDiagnostics {
    /** Physical render-target width/height used for the current scene projection. */
    var surfaceAspect: Float = 16f / 9f
        internal set
    var frustumCulled: Int = 0
        internal set
    var occluded: Int = 0
        internal set
    var submittedDrawCalls: Int = 0
        internal set
    var submittedInstances: Int = 0
        internal set

    /** Draws extracted from the scene but rejected by the backend resource resolver. */
    var unresolvedDrawCalls: Int = 0
        internal set
}
