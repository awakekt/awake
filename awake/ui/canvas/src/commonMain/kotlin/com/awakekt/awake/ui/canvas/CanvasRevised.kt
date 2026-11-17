/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

/**
 * A canvas part that counts its own changes: every settable property routes its write through
 * [revised], which bumps [revision] when the value it receives differs from the one it holds.
 *
 * A host that skips frames whose inputs did not change reads [revision] to see writes it cannot
 * otherwise observe, such as a system setting `rect.offsetX` on a component it got from the world
 * (#1512). An equal write leaves [revision] alone, so a system that rewrites the same position or
 * text every frame does not force a frame that would otherwise be skipped.
 *
 * Primitives compare as primitives: no boxing, so a set allocates nothing on the JVM or Kotlin/Wasm.
 * Floats compare as IEEE values: `-0f` equals `0f`, and a `NaN` differs from everything, itself
 * included, so writing `NaN` bumps on every write. References compare by identity, then by `==`.
 * Outputs a part computes for itself, such as [CanvasRect.screenBounds], are not counted.
 */
abstract class CanvasRevised {
    /** How many times a property of this part has been set to a different value; only ever grows, and may wrap. */
    val revision: Int get() = count

    private var count = 0

    /**
     * The part this one reports its changes to: a component's element, and an element's
     * [CanvasChanges]. A change here counts there too, so a host reads one counter, not every part.
     */
    internal var reportsTo: CanvasRevised? = null

    /** Makes this part the one [part] reports to, after [previous], which it held there before, stops. */
    internal fun <P : CanvasRevised?> adopt(previous: CanvasRevised?, part: P): P {
        if (previous !== part && previous?.reportsTo === this) previous.reportsTo = null
        part?.reportsTo = this
        return part
    }

    /** Counts a change here and in every part above, by [reportsTo]. */
    private fun changed() {
        var part: CanvasRevised? = this
        // A loop, not recursion: a link made into a cycle by mistake still ends after a few hops.
        var hops = 0
        while (part != null && hops++ < MAX_HOPS) {
            part.count++
            part = part.reportsTo
        }
    }

    /** Returns [new], counting a revision when it differs from [old]. */
    protected fun revised(old: Float, new: Float): Float {
        if (old != new) changed()
        return new
    }

    /** Returns [new], counting a revision when it differs from [old]. */
    protected fun revised(old: Int, new: Int): Int {
        if (old != new) changed()
        return new
    }

    /** Returns [new], counting a revision when it differs from [old]. */
    protected fun revised(old: Long, new: Long): Long {
        if (old != new) changed()
        return new
    }

    /** Returns [new], counting a revision when it differs from [old]. */
    protected fun revised(old: Double, new: Double): Double {
        if (old != new) changed()
        return new
    }

    /** Returns [new], counting a revision when it differs from [old]. */
    protected fun revised(old: Boolean, new: Boolean): Boolean {
        if (old != new) changed()
        return new
    }

    /** Returns [new], counting a revision when it is neither the same object as [old] nor equal to it. */
    protected fun <T> revised(old: T, new: T): T {
        if (old !== new && old != new) changed()
        return new
    }

    /** Counts a revision for a change the part makes other than through a property setter, such as filling a list in place. */
    protected fun revise() {
        changed()
    }

    private companion object {
        /** Part, element, canvas: more hops than that is a cycle. */
        const val MAX_HOPS = 4
    }
}

/**
 * Counts every change to the elements that report to it, through [CanvasElementBase.changes]: one
 * number a host that skips unchanged frames reads in place of walking every element (#1512).
 */
class CanvasChanges : CanvasRevised()
