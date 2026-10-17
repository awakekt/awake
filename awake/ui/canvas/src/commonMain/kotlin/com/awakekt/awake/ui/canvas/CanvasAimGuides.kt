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
            val skin = tree.skinOf(element)?.button
            val state = element.controlState()
            val ring = Modifier.offset((centerX - reach).dp, (centerY - reach).dp).size((reach * 2f).dp).testTag("canvas-aim-range")
            val range = skin?.aimRange
            if (range != null) Slot(element, CanvasSlots.BUTTON_AIM_RANGE, range, state, ring, tree.images, round = true) else Box(ring.clip(CircleShape).background(RANGE))
            val cursorSlot = skin?.aimCursor
            val cursor = cursorSlot?.size ?: CURSOR_SIZE
            val point = Modifier.offset((centerX + element.aimX * reach - cursor / 2f).dp, (centerY + element.aimY * reach - cursor / 2f).dp)
                .size(cursor.dp).testTag("canvas-aim-cursor")
            if (cursorSlot != null) {
                Slot(element, CanvasSlots.BUTTON_AIM_CURSOR, cursorSlot, state, point, tree.images, round = true)
            } else {
                Box(point.clip(CircleShape).background(if (element.aimCancelling) CANCELLING else CURSOR))
            }
            aim.cancel?.let { zone -> CancelZone(element, zone, tree, safeArea) }
        }
    }
}

/** This button's aim while it is being dragged to aim and is shown, or null. */
private fun CanvasElement.shownAim(tree: CanvasTree): CanvasAim? = button?.aim?.takeIf { aiming && tree.shows(this) }

/** [zone] against the canvas, its placed box written to [element]'s press as the cancel bounds. */
context(_: Composer)
private fun BoxScope.CancelZone(element: CanvasElement, zone: CanvasZone, tree: CanvasTree, safeArea: CanvasInsets) {
    val slot = tree.skinOf(element)?.button?.cancelZone
    val units = LocalDensity.current
    val origin = tree.origin
    Box(Modifier.align(zone.anchor.alignment).anchorInset(zone.anchor, zone.offsetX, zone.offsetY, safeArea)) {
        val placed = Modifier.size(zone.width.dp, zone.height.dp).testTag("canvas-aim-cancel")
            .onPlaced { x, y, w, h ->
                element.buttonPress.cancelBounds = Rectangle((x - origin.x) / units, (y - origin.y) / units, w / units, h / units)
            }
        if (slot != null) {
            Slot(element, CanvasSlots.BUTTON_CANCEL_ZONE, slot, element.controlState(), placed, tree.images)
        } else {
            Box(placed.background(if (element.aimCancelling) CANCELLING else CANCEL_ZONE))
        }
    }
}

private val RANGE = Color(1f, 1f, 1f, 0.12f)
private val CURSOR = Color(1f, 1f, 1f, 0.8f)
private val CANCEL_ZONE = Color(0f, 0f, 0f, 0.45f)
private val CANCELLING = Color(0.9f, 0.25f, 0.25f, 0.85f)
private const val CURSOR_SIZE = 28f
