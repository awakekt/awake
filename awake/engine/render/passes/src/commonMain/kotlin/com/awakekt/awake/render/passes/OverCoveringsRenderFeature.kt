/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

/**
 * The scene's [com.awakekt.awake.render.command.PreparedDraw.overCoverings] draws, recorded after
 * the covering content features ([ContentPaint.AfterGeometry]) and before the UI.
 *
 * A covering, such as distance fog, blends over every pixel the scene's depth holds and cannot tell
 * one surface from another. A draw that must reach the target as its shader left it, an unlit
 * material, is therefore drawn after them, in the same scene pass and against the depth the opaque
 * draws already wrote, so it still hides behind nearer geometry and covers what the fog left on
 * the pixels it wins.
 *
 * A capability, not authored content: always present, and records nothing on a frame without
 * such draws.
 *
 * @param C The backend's frame-context type.
 */
class OverCoveringsRenderFeature<C : RenderFrameContext> : RenderFeature<C> {
    override val pass = RenderPassSlot.Scene

    private val shared = SharedOpaqueRenderFeature()

    override fun recordCommands(context: C): Unit = with(context) {
        val grouped = overCoveringDrawCalls
        if (grouped.isEmpty()) return
        shared.recordCommands(recorder = recorder, primaryPipeline = primaryPipeline, grouped = grouped)
    }

    /** Nothing: the draws' pipelines and buffers belong to the backend. */
    override fun destroy() = Unit
}
