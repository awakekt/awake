/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

private const val REVISION_MIX = 31
private const val ABSENT = -1

/**
 * Changes to anything the element draws from, for a host that skips unchanged frames (#1512): its
 * own fields, every component and widget it holds and what each holds, and which components those
 * are. It moves on every write that changes a value, through the element or straight to a
 * component, and stays put on a write of the value already there.
 *
 * The parts are mixed, not added, so a change in one part cannot be cancelled by a change in another,
 * and [CanvasElementBase.componentsVersion] moves whenever a component is replaced, whose own
 * [CanvasRevised.revision] starts again. Compare it with the value read last time to tell "changed"
 * from "not"; the number means nothing else. Reading it allocates nothing.
 */
val CanvasElementBase.contentRevision: Int
    get() {
        var hash = revision * REVISION_MIX + componentsVersion
        hash = hash * REVISION_MIX + rect.revision
        hash = hash * REVISION_MIX + pointer.revision
        hash = hash * REVISION_MIX + (textPart?.revision ?: ABSENT)
        hash = hash * REVISION_MIX + (appearancePart?.revision ?: ABSENT)
        hash = hash * REVISION_MIX + (followPart?.revision ?: ABSENT)
        hash = hash * REVISION_MIX + (arrowPart?.revision ?: ABSENT)
        hash = hash * REVISION_MIX + (inputPart?.revision ?: ABSENT)
        hash = hash * REVISION_MIX + (tooltipPart?.revision ?: ABSENT)
        hash = hash * REVISION_MIX + (particlesPart?.revision ?: ABSENT)
        return hash * REVISION_MIX + ((widget as? CanvasRevised)?.revision ?: ABSENT)
    }
