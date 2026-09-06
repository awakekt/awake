/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot

/**
 * The backend half of [RenderFeature2D].
 */
interface DrawPass<M, in C : RenderFrameContext> {
    /** This frame's uploaded runs, in paint order. */
    fun runs(context: C): List<DrawRun<M>>

    /** `null` when this frame built no 2D pipelines at all, which skips the pass. */
    fun recorder(context: C): DrawRunRecorder<M>?
}

typealias UiPass<M, C> = DrawPass<M, C>

/**
 * The 2D draw overlay pass's content -- this frame's runs in original paint order.
 */
class RenderFeature2D<M, C : RenderFrameContext>(private val drawPass: DrawPass<M, C>) : RenderFeature<C> {
    override val pass = RenderPassSlot.Ui

    private val shared = SharedRenderFeature2D()

    override fun recordCommands(context: C) {
        val recorder = drawPass.recorder(context) ?: return
        shared.recordCommands(
            runs = drawPass.runs(context),
            surfaceWidth = context.surfaceWidth,
            surfaceHeight = context.surfaceHeight,
            recorder = recorder,
        )
    }

    override fun destroy() = Unit
}

typealias UiRenderFeature<M, C> = RenderFeature2D<M, C>
