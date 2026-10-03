/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.scene.rendering.debug.RenderDiagnostics
import com.awakekt.awake.scene.runtime.LocalFrameStats
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

/**
 * What the last frames cost, split the way a frame is spent.
 *
 * Average FPS on its own hides the stalls a player feels, so p99 and the worst frame of the last
 * few seconds sit beside it. Game, Render, UI, Wait and GPU are where the frame's time went, shown
 * while phase timing is on (F2); draws and triangles are what the backend recorded.
 */
context(_: Composer)
internal fun ShowcaseStatsCard(modifier: Modifier = Modifier) {
    val frameStats = LocalFrameStats.current
    val phases = frameStats.phases
    val render = frameStats.render
    Column(
        modifier = modifier.width(CARD_WIDTH).background(ShowcaseTheme.palette.card).padding(CARD_INSET),
    ) {
        ShadcnText("FPS: ${frameStats.fps.oneDecimal()}  Frame: ${frameStats.frameTimeMs.oneDecimal()}ms", variant = ShadcnTextVariant.Small)
        ShadcnText("p99: ${frameStats.p99FrameTimeMs.oneDecimal()}ms  Worst: ${frameStats.maxFrameTimeMs.oneDecimal()}ms", variant = ShadcnTextVariant.Small)
        if (phases.isMeasured) {
            ShadcnText("Game: ${phases.gameMs.oneDecimal()}ms  Render: ${phases.renderMs.oneDecimal()}ms", variant = ShadcnTextVariant.Small)
            ShadcnText("UI: ${(phases.uiBuildMs + phases.uiStageMs).oneDecimal()}ms  Wait: ${phases.uiWaitMs.oneDecimal()}ms", variant = ShadcnTextVariant.Small)
            // With the other timings: a reading that changes every frame stays off until asked for.
            ShadcnText("GPU: ${render?.gpuTimeMs?.let { "${it.oneDecimal()}ms" } ?: "not timed"}", variant = ShadcnTextVariant.Small)
        } else {
            ShadcnText("Press F2 for phase timings", variant = ShadcnTextVariant.Small)
        }
        // Visible: renderables that survived culling, one draw each before instancing folds them.
        // Recorded: every draw the backend issued, shadow cascades and UI included.
        ShadcnText("Visible: ${RenderDiagnostics.submittedInstances.toLong().compact()} renderables", variant = ShadcnTextVariant.Small)
        if (render != null) {
            ShadcnText("Recorded: ${render.drawCalls} draws, ${render.triangles.compact()} tris", variant = ShadcnTextVariant.Small)
        }
        ShadcnText("Culled: ${RenderDiagnostics.frustumCulled}  Occluded: ${RenderDiagnostics.occluded}", variant = ShadcnTextVariant.Small)
        ShadcnText("Unresolved: ${RenderDiagnostics.unresolvedDrawCalls}", variant = ShadcnTextVariant.Small)
    }
}

/** 950, 12.3k, 4.1M: readable at a glance in a narrow card. */
private fun Long.compact(): String = when {
    this < COMPACT_THOUSAND -> toString()
    this < COMPACT_MILLION -> "${(this / 100L) / 10f}k"
    else -> "${(this / 100_000L) / 10f}M"
}

private const val COMPACT_THOUSAND = 10_000L
private const val COMPACT_MILLION = 1_000_000L
