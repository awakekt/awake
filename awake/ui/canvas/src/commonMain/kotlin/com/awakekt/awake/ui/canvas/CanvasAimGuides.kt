/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxScope
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.graphics.CircleShape
import com.awakekt.awake.compose.ui.layout.onPlaced
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Rectangle

/**
 * What every aimed action button being dragged shows over the canvas: its reach as a ring around
 * the button, a cursor at the aim, and its cancel zone, placed against the canvas inside
 * [safeArea]. Where the zone lands is written back to the button, so a release over it cancels.
 */
context(_: Composer)
internal fun BoxScope.AimGuides(tree: CanvasTree, safeArea: CanvasInsets) {
    for (item in tree.items) {
        val element = item.element
        val aim = element.shownAim(tree)
        val bounds = element.placedBounds
        if (aim == null || bounds == null) continue
        key(element) {
            val centerX = bounds.x + bounds.width / 2f
            val centerY = bounds.y + bounds.height / 2f
            val reach = aim.reach
            Box(Modifier.offset((centerX - reach).dp, (centerY - reach).dp).size((reach * 2f).dp).clip(CircleShape).background(RANGE).testTag("canvas-aim-range"))
            val cursorX = centerX + element.aimX * reach - CURSOR_SIZE / 2f
            val cursorY = centerY + element.aimY * reach - CURSOR_SIZE / 2f
            val cursorColor = if (element.aimCancelling) CANCELLING else CURSOR
            Box(Modifier.offset(cursorX.dp, cursorY.dp).size(CURSOR_SIZE.dp).clip(CircleShape).background(cursorColor).testTag("canvas-aim-cursor"))
            aim.cancel?.let { zone -> CancelZone(element, zone, tree.origin, safeArea) }
        }
    }
}

/** This button's aim while it is being dragged to aim and is shown, or null. */
private fun CanvasElement.shownAim(tree: CanvasTree): CanvasAim? = button?.aim?.takeIf { aiming && tree.shows(this) }

/** [zone] against the canvas, its placed box written to [element]'s press as the cancel bounds. */
context(_: Composer)
private fun BoxScope.CancelZone(element: CanvasElement, zone: CanvasZone, origin: CanvasOrigin, safeArea: CanvasInsets) {
    val units = LocalDensity.current
    val color = if (element.aimCancelling) CANCELLING else CANCEL_ZONE
    Box(Modifier.align(zone.anchor.alignment).anchorInset(zone.anchor, zone.offsetX, zone.offsetY, safeArea)) {
        Box(
            Modifier.size(zone.width.dp, zone.height.dp).background(color).testTag("canvas-aim-cancel")
                .onPlaced { x, y, w, h ->
                    element.buttonPress.cancelBounds = Rectangle((x - origin.x) / units, (y - origin.y) / units, w / units, h / units)
                },
        )
    }
}

private val RANGE = Color(1f, 1f, 1f, 0.12f)
private val CURSOR = Color(1f, 1f, 1f, 0.8f)
private val CANCEL_ZONE = Color(0f, 0f, 0f, 0.45f)
private val CANCELLING = Color(0.9f, 0.25f, 0.25f, 0.85f)
private const val CURSOR_SIZE = 28f
