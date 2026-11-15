/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

/** A stage of [ComposeHost.frame], in the order it runs. */
enum class ComposeFrameStage {
    /** Composing the content, or skipping a composition nothing invalidated. */
    Compose,

    /** Measuring and placing the tree, or reusing last frame's layout. */
    Measure,

    /** Painting the tree into the frame's primitives and layers. */
    Draw,
}

/**
 * Told when each [ComposeFrameStage] of a [ComposeHost.frame] starts and ends, for a profile that
 * reads the clock or the heap at those marks. [begin] and [end] come in pairs, in stage order, once
 * each per frame. Input dispatch, focus and semantics run outside them.
 *
 * Install one as [ComposeHost.frameStages]. None is installed by default, and then nothing is called.
 */
interface ComposeFrameStages {
    /** [stage] is about to run. */
    fun begin(stage: ComposeFrameStage)

    /** [stage] has run. */
    fun end(stage: ComposeFrameStage)
}
