/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.core.input.Key

/**
 * How a resizable panel group's split moves when a handle is dragged.
 *
 * Here rather than in `:compose:foundation` for the reason stated in `ShadcnSliderMath`: Compose
 * Foundation ships no resizable-panel anything, and a local invention inside the parity group costs
 * the group its parity claim.
 *
 * Panels are stored as **fractions of the group's main axis**, not pixels, so a group that resizes
 * keeps its proportions instead of its absolute splits. Every function here works in that space; the
 * caller converts once, at the edge.
 *
 * **Compared against `shadcn-compose`, which solves a smaller problem.** Its group is two panels
 * driven by one `fraction`, rendering as `weight(fraction)` and `weight(1f - fraction)`. Conservation
 * there is *structural* -- one degree of freedom cannot drift -- so its whole update is a single
 * `coerceIn`. That is strictly better where it applies, and it does not apply here: this group is
 * N panels, each with its own stored fraction, because Studio's shape is sidebar/viewport/inspector
 * and `ResizablePanelDragConservationTest` pins three-panel behaviour. With N fractions conservation
 * has to be arithmetic, which is what [shadcnMoveSplit] is for.
 *
 * Its keyboard support had no counterpart here at all and is taken as-is -- see
 * [shadcnResizeKeyDelta].
 */

/** One panel's bounds in fraction space. */
data class ShadcnPanelLimits(val min: Float, val max: Float)

/** What a drag resolved to: the pair's new fractions. */
data class ShadcnPanelSplit(val before: Float, val after: Float)

/**
 * Moves the split between two adjacent panels by [rawDelta], conserving their total.
 *
 * **The invariant, and it is the whole reason this is a function rather than two `coerceIn` calls:**
 * whatever either panel's limits do, the pair's sum is unchanged. A group whose panels do not sum to
 * a constant drifts a little on every drag, and the drift is invisible per-frame and obvious after a
 * minute of dragging.
 *
 * Naively clamping each side independently breaks that the moment one side clamps harder than the
 * other -- the shrinking panel stops at its minimum while the growing one keeps going, and the group
 * gains the difference. So `after`'s clamped movement is measured first and `before` is moved by
 * exactly that, rather than each being clamped against its own limits and hoping they agree.
 *
 * Re-deriving from `after` is always safe, and not by luck: `after`'s clamp can only ever *reduce*
 * the magnitude of the movement. `before` was already feasible at the larger magnitude, so it is
 * still feasible at the smaller one. That is why one re-derivation suffices and no second pass is
 * needed.
 *
 * [rawDelta] is a fraction of the main axis -- pointer pixels divided by the axis length -- so a
 * caller never converts twice.
 */
fun shadcnMoveSplit(
    beforeFraction: Float,
    afterFraction: Float,
    rawDelta: Float,
    beforeLimits: ShadcnPanelLimits,
    afterLimits: ShadcnPanelLimits,
): ShadcnPanelSplit {
    val wanted = (beforeFraction + rawDelta).coerceIn(beforeLimits.min, beforeLimits.max) - beforeFraction
    val afterNew = (afterFraction - wanted).coerceIn(afterLimits.min, afterLimits.max)
    val applied = afterFraction - afterNew
    return ShadcnPanelSplit(before = beforeFraction + applied, after = afterNew)
}

/**
 * The fraction of the main axis a pointer movement of [pointerDelta] pixels represents.
 *
 * A zero or negative axis returns 0f rather than dividing: a group measured to nothing still
 * receives pointer events during the frame it collapses, and a division there would poison every
 * stored fraction with a non-finite value that never recovers.
 */
fun shadcnDragFraction(pointerDelta: Float, availableMainAxisPx: Float): Float {
    if (availableMainAxisPx <= 0f) return 0f
    return pointerDelta / availableMainAxisPx
}

/**
 * Distributes [count] panels evenly, for a group whose panels declare no explicit default.
 *
 * Returns an empty list for a non-positive [count] rather than throwing -- a group with no panels is
 * a legitimate empty state, not a programming error.
 */
fun shadcnEvenFractions(count: Int): List<Float> =
    if (count <= 0) emptyList() else List(count) { 1f / count }

/** One keyboard nudge, in px. Matches `react-resizable-panels`, which real shadcn wraps. */
const val SHADCN_RESIZE_KEY_STEP_PX = 24f

/**
 * Home/End, as a delta large enough that [shadcnMoveSplit]'s own clamp saturates to the boundary.
 *
 * Reuses that clamp rather than adding a "jump straight to min/max" branch -- one code path means
 * Home cannot disagree with what a long drag leftwards does, which is exactly the kind of drift two
 * paths produce.
 */
const val SHADCN_RESIZE_KEY_JUMP_PX = 1_000_000f

/**
 * The drag delta a key press means for a resize handle, or `null` for a key the handle does not
 * claim so the caller leaves it unconsumed.
 *
 * Keyboard resizing is a Radix/APG behaviour the old `ui-headless` group never had -- it was
 * pointer-only, so a resizable layout was unreachable without a mouse. Taken from `shadcn-compose`,
 * which had it.
 */
fun shadcnResizeKeyDelta(key: Key, horizontal: Boolean): Float? {
    val forward = if (horizontal) Key.ArrowRight else Key.ArrowDown
    val backward = if (horizontal) Key.ArrowLeft else Key.ArrowUp
    return when (key) {
        forward -> SHADCN_RESIZE_KEY_STEP_PX
        backward -> -SHADCN_RESIZE_KEY_STEP_PX
        Key.End -> SHADCN_RESIZE_KEY_JUMP_PX
        Key.Home -> -SHADCN_RESIZE_KEY_JUMP_PX
        else -> null
    }
}
