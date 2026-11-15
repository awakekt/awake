/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

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

    override fun toString(): String =
        "ComposeFrameCounts(composed=$composed, measured=$measured, nodesComposed=$nodesComposed, " +
            "nodesMeasured=$nodesMeasured, nodesDrawn=$nodesDrawn, subtreesReplayed=$subtreesReplayed)"
}
