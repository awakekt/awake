// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui

import io.github.ronjunevaldoz.awake.core.math2d.dp
import io.github.ronjunevaldoz.awake.ui.api.layout.Dimension
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.ui.context.UiContext
import io.github.ronjunevaldoz.awake.ui.context.UiFrameInput
import io.github.ronjunevaldoz.awake.ui.layouts.Arrangement
import io.github.ronjunevaldoz.awake.ui.layouts.column
import io.github.ronjunevaldoz.awake.ui.layouts.row
import io.github.ronjunevaldoz.awake.ui.modifier.Modifier
import io.github.ronjunevaldoz.awake.ui.modifier.fillMaxHeight
import io.github.ronjunevaldoz.awake.ui.modifier.fillMaxWidth
import io.github.ronjunevaldoz.awake.ui.modifier.height
import io.github.ronjunevaldoz.awake.ui.modifier.width
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 1 parity regression: `fillMaxWidth()` / `fillMaxHeight()` in an unbounded (wrap-content)
 * parent must NOT expand to the entire frame viewport.
 *
 * Compose contract (FillNode source):
 *   `constraints.maxWidth * fraction` -- when `maxWidth == Constraints.Infinity`, the result is
 *   effectively 0 (no-op), so the child falls through to its own intrinsic/wrap-content size.
 *
 * Pre-fix Awake behavior: `this.width ?: frameBoundsInternal().width` → full viewport width (BUG).
 * Post-fix Awake behavior: `!hasBoundedFillWidth → return 0f` → child wraps its content.
 *
 * Reference: docs/reference/compose-modifier-layout-guidance.md §2,
 *            docs/audits/2026-08-21-compose-layout-modifier-parity-plan.md Phase 1.
 */
class FillMaxUnboundedParentTest {

    private val FRAME = 400f
    private val noGap = Arrangement.spacedBy(0f.dp)

    private fun makeBox() = run {
        val ui = UiContext()
        ui.beginFrame(
            UiFrameInput(
                viewportWidth = FRAME,
                viewportHeight = FRAME,
                input = testSnapshot(),
            ),
        )
        ui to ui.createBox(x = 0f, y = 0f, width = FRAME, height = FRAME)
    }

    // ── Row / width axis ───────────────────────────────────────────────────────────────────────

    /**
     * Regression: `fillMaxWidth()` child inside an unbounded (no explicit width) `row`.
     * Must NOT blow out to `FRAME` width — must stay at 0 (empty content / no intrinsic size).
     *
     * This was the exact button-blowout bug: a widget with `fillMaxWidth()` inside a wrap-content
     * `row` expanded to the full frame width (400px).
     */
    @Test
    fun fillMaxWidth_inUnboundedRow_doesNotExpandToFrameWidth() {
        val (_, outer) = makeBox()
        var child: Rectangle? = null
        outer.column(
            id = "host",
            verticalArrangement = noGap,
            modifier = Modifier.width(FRAME.dp).height(FRAME.dp),
        ) {
            // wrap-content row — explicit WrapContent width → hasBoundedFillWidth = false
            row(
                modifier = Modifier.width(Dimension.WrapContent),
                verticalAlignment = io.github.ronjunevaldoz.awake.ui.api.layout.UiAlignment.Vertical.Top,
            ) {
                child = column(modifier = Modifier.fillMaxWidth()) { }
            }
        }
        val w = child!!.width
        assertTrue(
            w < FRAME,
            "fillMaxWidth() in unbounded row should NOT expand to frame width ($FRAME), got $w",
        )
    }

    /**
     * Control: `fillMaxWidth()` in a row WITH an explicit width must still fill that bound.
     */
    @Test
    fun fillMaxWidth_inBoundedRow_expandsToParentWidth() {
        val PARENT = 200f
        val (_, outer) = makeBox()
        var child: Rectangle? = null
        outer.column(
            id = "host",
            verticalArrangement = noGap,
            modifier = Modifier.width(FRAME.dp).height(FRAME.dp),
        ) {
            row(modifier = Modifier.width(PARENT.dp)) {
                child = column(modifier = Modifier.fillMaxWidth()) { }
            }
        }
        val w = child!!.width
        assertTrue(
            w >= PARENT - 1f,
            "fillMaxWidth() in bounded row ($PARENT) should fill it, got $w",
        )
    }

    // ── Column / height axis ───────────────────────────────────────────────────────────────────

    /**
     * Regression: `fillMaxHeight()` child inside an unbounded (no explicit height) `column`.
     * Must NOT blow out to `FRAME` height.
     */
    @Test
    fun fillMaxHeight_inUnboundedColumn_doesNotExpandToFrameHeight() {
        val (_, outer) = makeBox()
        var child: Rectangle? = null
        outer.column(
            id = "host",
            verticalArrangement = noGap,
            modifier = Modifier.width(FRAME.dp).height(FRAME.dp),
        ) {
            // wrap-content column — no explicit height → hasBoundedFillHeight = false
            column(id = "unbounded-col") {
                child = column(modifier = Modifier.fillMaxHeight()) { }
            }
        }
        val h = child!!.height
        assertTrue(
            h < FRAME,
            "fillMaxHeight() in unbounded column should NOT expand to frame height ($FRAME), got $h",
        )
    }

    /**
     * Control: `fillMaxHeight()` in a column WITH an explicit height must still fill that bound.
     */
    @Test
    fun fillMaxHeight_inBoundedColumn_expandsToParentHeight() {
        val PARENT = 200f
        val (_, outer) = makeBox()
        var child: Rectangle? = null
        outer.column(
            id = "host",
            verticalArrangement = noGap,
            modifier = Modifier.width(FRAME.dp).height(FRAME.dp),
        ) {
            column(id = "bounded-col", modifier = Modifier.height(PARENT.dp)) {
                child = column(modifier = Modifier.fillMaxHeight()) { }
            }
        }
        val h = child!!.height
        assertTrue(
            h >= PARENT - 1f,
            "fillMaxHeight() in bounded column ($PARENT) should fill it, got $h",
        )
    }

    // ── Cross-axis guard — existing behavior must be preserved ─────────────────────────────────

    /**
     * `fillMaxHeight()` cross-axis inside a row that has an explicit height must still resolve to
     * that height. Verifies the fix does not break the cross-axis fill path.
     */
    @Test
    fun fillMaxHeight_inRow_withBoundedHeight_stillFills() {
        val ROW_H = 48f
        val (_, outer) = makeBox()
        var child: Rectangle? = null
        outer.column(
            id = "host",
            verticalArrangement = noGap,
            modifier = Modifier.width(FRAME.dp).height(FRAME.dp),
        ) {
            row(modifier = Modifier.height(ROW_H.dp)) {
                child = column(modifier = Modifier.fillMaxHeight()) { }
            }
        }
        val h = child!!.height
        assertTrue(
            h >= ROW_H - 1f,
            "fillMaxHeight() cross-axis in bounded row ($ROW_H) should fill it, got $h",
        )
    }
}

