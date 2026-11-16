/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

import com.awakekt.awake.compose.runtime.PhaseReadCause
import com.awakekt.awake.compose.runtime.PhaseReads
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints

/**
 * Decides, for [ComposeHost.skipUnchangedMeasure], whether a frame's layout measures or only places
 * (#1512, the placement-only path), and records what measuring read.
 *
 * A frame measures when its composition ran (the tree, its modifiers or the viewport may have
 * changed), when the last measure read a state that changed since, or when skipping is off. Anything
 * else is a frame in which only values read while placing or drawing can differ, so the tree keeps
 * its sizes and is placed again.
 */
internal class MeasureSkip {
    private val reads = PhaseReads()

    /** Whether [cause] and its source and owner are kept; see [PhaseReads.explains]. */
    var explains: Boolean
        get() = reads.explains
        set(value) {
            reads.explains = value
        }

    /** Why the last [layout] measured although its composition was skipped, while [explains]. */
    var cause: PhaseReadCause = PhaseReadCause.None
        private set
    var causeSource: Any? = null
        private set
    var causeOwner: Any? = null
        private set

    /**
     * Lays [root] out for [viewport] and returns whether it skipped measuring. With [enabled] off it
     * always measures and records nothing, so the next recorded pass starts out of date.
     */
    fun layout(root: LayoutNode, viewport: Constraints, enabled: Boolean, composed: Boolean): Boolean {
        if (!enabled) {
            reads.invalidate()
            root.layoutTree(viewport, measure = true, reads = null)
            return false
        }
        val measure = composed || reads.isInvalid
        // Read before the pass below starts another and forgets it.
        cause = if (composed) PhaseReadCause.None else reads.cause
        causeSource = if (composed) null else reads.causeSource
        causeOwner = if (composed) null else reads.causeOwner
        root.layoutTree(viewport, measure = measure, reads = reads)
        return !measure
    }

    /** Makes the next frame measure, as after the tree was dropped. */
    fun invalidate() {
        reads.forget()
    }
}
