/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

/**
 * Working memory [ScenePassCompiler.compile] keeps from one frame to the next. A caller that
 * compiles every frame, such as a scene's planner, holds one and passes it each time; what a
 * compile returns stays valid until that scratch's next compile.
 *
 * It also remembers what the last compile read. When a frame reads the same, and the preparer
 * [redraws prepared frames][com.awakekt.awake.render.command.GpuDrawPreparer.redrawsPreparedFrames],
 * compile hands back the last frame's input without folding, preparing or sorting anything.
 */
class ScenePassScratch {
    internal val batcher = InstanceBatcher()
    internal val casters = ShadowCasterFolding()

    /** Each caster's reach toward the light; null scans every caster in every cascade, as a reference. */
    internal var reach: LightDepthReach? = LightDepthReach()
    internal val signature = PassInputSignature()
    internal var lastInput: com.awakekt.awake.render.command.GpuPassInput? = null
    internal var lastStats: SceneDrawStats? = null

    /** Whether the last compile handed back the frame before's input, nothing having changed. */
    var reusedLastFrame: Boolean = false
        internal set

    /** Plans the next frame in full, whatever it reads. */
    fun forgetLastFrame() {
        signature.clear()
        lastInput = null
    }
}
