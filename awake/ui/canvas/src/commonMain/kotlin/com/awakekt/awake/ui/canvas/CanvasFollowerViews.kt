/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.graphicsLayer
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A follower where its [plan] puts it, at the plan's density, scale and opacity. */
context(_: Composer)
internal fun Follower(element: CanvasElement, plan: FollowerPlan, tag: String, tree: CanvasTree) {
    val currentDensity = LocalDensity.current
    val baseDensity = plan.density.takeIf { it > 0f } ?: currentDensity
    if (baseDensity != currentDensity) {
        CompositionLocalProvider(LocalDensity provides baseDensity) {
            PlacedFollower(element, plan, tag, tree, baseDensity)
        }
    } else {
        PlacedFollower(element, plan, tag, tree, baseDensity)
    }
}

context(_: Composer)
private fun PlacedFollower(element: CanvasElement, plan: FollowerPlan, tag: String, tree: CanvasTree, baseDensity: Float) {
    val box = plan.box
    val faded = if (plan.alpha < 1f) Modifier.graphicsLayer(alpha = plan.alpha) else Modifier
    Box(Modifier.offset(box.x.dp, box.y.dp).then(faded)) {
        if (plan.scale == 1f) {
            CanvasElementView(element, Modifier.size(box.width.dp, box.height.dp).testTag(tag), tree, tree.childrenOf(element))
        } else {
            // A denser dp scales its text, images and children with it; its box is already scaled.
            CompositionLocalProvider(
                LocalDensity provides baseDensity * plan.scale,
                LocalFollowerScale provides plan.scale,
            ) {
                CanvasElementView(element, Modifier.size(element.width.dp, element.height.dp).testTag(tag), tree, tree.childrenOf(element))
            }
        }
    }
}

/** The distance-curve scale active over [LocalDensity] inside a scaled [Follower]; 1 outside one. */
internal val LocalFollowerScale = compositionLocalOf { 1f }

/**
 * A pinned [owner]'s [item], centred where the line from the owner's centre toward its target
 * leaves the owner's box, and turned to point that way.
 */
context(_: Composer)
internal fun PinArrow(item: CanvasItem, owner: CanvasElement, tag: String, tree: CanvasTree) {
    val arrow = item.element
    val parentDensity = LocalDensity.current
    val arrowDensity = item.scaler?.let(tree.box::densityOf) ?: parentDensity
    val ratio = if (parentDensity > 0f) arrowDensity / parentDensity else 1f
    val radians = owner.pinAngle / DEGREES_PER_RADIAN
    val dx = cos(radians)
    val dy = sin(radians)
    val halfWidth = owner.width / 2f
    val halfHeight = owner.height / 2f
    val alongX = if (abs(dx) > EDGE_EPSILON) halfWidth / abs(dx) else Float.MAX_VALUE
    val alongY = if (abs(dy) > EDGE_EPSILON) halfHeight / abs(dy) else Float.MAX_VALUE
    val along = minOf(alongX, alongY)
    val x = halfWidth + dx * along - arrow.width * ratio / 2f
    val y = halfHeight + dy * along - arrow.height * ratio / 2f
    arrow.arrowAngle = owner.pinAngle
    Box(Modifier.offset(x.dp, y.dp)) {
        WithItemScaler(item, tree) {
            CanvasElementView(arrow, Modifier.size(arrow.width.dp, arrow.height.dp).testTag(tag), tree, tree.childrenOf(arrow))
        }
    }
}

private const val EDGE_EPSILON = 1e-4f

private const val DEGREES_PER_RADIAN = (180.0 / PI).toFloat()
