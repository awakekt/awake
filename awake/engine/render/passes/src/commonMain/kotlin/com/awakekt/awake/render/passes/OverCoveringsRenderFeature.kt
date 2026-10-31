/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

/**
 * The rest of the scene pass, recorded after the covering content features
 * ([ContentPaint.AfterGeometry]) and before the UI: the
 * [com.awakekt.awake.render.command.PreparedDraw.overCoverings] draws, then the wireframe edges,
 * the transparent draws and the debug lines, in the order [OpaqueRenderFeature] records them
 * without coverings.
 *
 * A covering, such as distance fog, blends over every pixel the scene's depth holds and cannot tell
 * one surface from another. A draw that must reach the target as its shader left it, an unlit
 * material, is therefore drawn after it, against the depth the opaque draws already wrote, so it
 * still hides behind nearer geometry and covers what the fog left on the pixels it wins. What is
 * drawn over an opaque surface comes after it here too: a transparent draw in front of an unlit
 * one still blends over it.
 *
 * Pair it with an [OpaqueRenderFeature] built with `defersOverCoverings = true` on the same
 * [lines], which that feature owns and frees.
 *
 * @param C The backend's frame-context type.
 * @param lines The backend's debug-line pipeline and staged geometry, shared with the opaque feature.
 */
class OverCoveringsRenderFeature<C : RenderFrameContext>(private val lines: LinePass<C>) : RenderFeature<C> {
    override val pass = RenderPassSlot.Scene

    private val shared = SharedOpaqueRenderFeature()
    private val transparent = SharedTransparentRenderFeature(shared)

    override fun recordCommands(context: C) = shared.recordSceneOverlays(context, transparent, lines)

    /** Nothing: the paired [OpaqueRenderFeature] owns [lines], and the draws belong to the backend. */
    override fun destroy() = Unit
}
