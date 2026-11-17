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
        hash = hash * REVISION_MIX + revisionOf(textPart)
        hash = hash * REVISION_MIX + revisionOf(appearancePart)
        hash = hash * REVISION_MIX + revisionOf(followPart)
        hash = hash * REVISION_MIX + revisionOf(arrowPart)
        hash = hash * REVISION_MIX + revisionOf(inputPart)
        hash = hash * REVISION_MIX + revisionOf(tooltipPart)
        hash = hash * REVISION_MIX + revisionOf(particlesPart)
        return hash * REVISION_MIX + revisionOf(widget as? CanvasRevised)
    }

/**
 * [part]'s revision, or [ABSENT] for none. Not `part?.revision ?: ABSENT`: on Kotlin/Wasm that boxes
 * the `Int?` between the two, an allocation for each part of each element a check reads (#1512).
 */
private fun revisionOf(part: CanvasRevised?): Int = if (part == null) ABSENT else part.revision
