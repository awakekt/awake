/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

import com.awakekt.awake.compose.runtime.PhaseReadCause

/**
 * What the last [ComposeHost.frame] did, for a host that wants to see what each of the host's skips
 * saves without a profiler (#1512). One object per host, rewritten each frame, so reading it
 * allocates nothing; copy the values to keep them.
 *
 * Composition, measure and draw are each decided for the whole frame: a frame that composed declares
 * every node again, and one that measured measures every node. Only draw replays subtree by subtree,
 * on a frame that did neither (see [ComposeHost.retainUnchangedDraw]).
 */
class ComposeFrameCounts internal constructor() {
    /** Whether the frame ran its composition, rather than skipping it ([ComposeHost.skipUnchangedComposition]). */
    var composed: Boolean = false
        internal set

    /** Whether the frame measured its tree, rather than placing last frame's layout ([ComposeHost.skipUnchangedMeasure]). */
    var measured: Boolean = false
        internal set

    /** Nodes the host's composition declared, reused or new; 0 when it skipped. Subcompositions are not counted. */
    var nodesComposed: Int = 0
        internal set

    /** Nodes measured, each counted once a measure. */
    var nodesMeasured: Int = 0
        internal set

    /** Nodes painted afresh. */
    var nodesDrawn: Int = 0
        internal set

    /** Subtrees replayed from last frame's draw instead of painted ([ComposeHost.retainUnchangedDraw]). */
    var subtreesReplayed: Int = 0
        internal set

    /**
     * Why a frame whose composition was skipped measured anyway, while [ComposeHost.explainMeasure]
     * is on: the first change found since the last measure. [PhaseReadCause.None] on a frame that
     * composed (composing always measures), on one that skipped measuring, or while not explaining.
     */
    var measureCause: PhaseReadCause = PhaseReadCause.None
        internal set

    /** The state or polled source behind [measureCause], if it has one. */
    var measureCauseSource: Any? = null
        internal set

    /**
     * The layout node whose measure read what changed, if [measureCause] came from a read: its
     * [com.awakekt.awake.compose.ui.node.LayoutNode.measurePolicy] and modifier chain say which
     * composable declared it.
     */
    var measureCauseOwner: Any? = null
        internal set

    /**
     * What placement changed that the frame's composition read, while [ComposeHost.explainPlacement]
     * is on: the first state written, polled value changed or `invalidate()` during this frame's
     * layout, on a frame whose composition was current before it. Such a frame composes and lays out
     * a second time ([ComposeHost.placementRecompositions]). [PhaseReadCause.None] on a frame where
     * placement changed nothing composition read, or while not explaining.
     */
    var placementCause: PhaseReadCause = PhaseReadCause.None
        internal set

    /** The state or polled source behind [placementCause], if it has one: what placement wrote. */
    var placementCauseSource: Any? = null
        internal set

    /** Who read [placementCauseSource] in composition, if the host named a reader while composing. */
    var placementCauseOwner: Any? = null
        internal set

    override fun toString(): String =
        "ComposeFrameCounts(composed=$composed, measured=$measured, nodesComposed=$nodesComposed, " +
            "nodesMeasured=$nodesMeasured, nodesDrawn=$nodesDrawn, subtreesReplayed=$subtreesReplayed, " +
            "measureCause=$measureCause)"
}
