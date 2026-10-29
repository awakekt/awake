/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxScope
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Places an element's children as a button cluster: the first, the primary, centred on a pivot, and
 * the rest around it on an arc, as secondary buttons fan out around a main one. A child keeps its
 * `width` and `height` and ignores its anchor and offsets.
 *
 * Angles are degrees clockwise from the right, as the screen has them: 180 is left of the pivot and
 * 270 above it. Keep the element's box big enough to hold the arc, since a child outside its
 * parent's box is drawn but may not take a touch.
 *
 * @property pivotX Where across the element the pivot is, from 0 at its left to 1 at its right.
 * @property pivotY Where down the element the pivot is, from 0 at its top to 1 at its bottom.
 * @property radius How far from the pivot, in dp, the secondary children's centres sit.
 * @property startAngle The first secondary child's angle.
 * @property spacing The angle from one secondary child to the next; negative goes anticlockwise.
 * @property primaryAtPivot Whether the first child sits on the pivot; false puts every child on the arc.
 */
@Serializable
data class CanvasCluster(
    @PropertyRange(min = 0.0, max = 1.0) val pivotX: Float = 1f,
    @PropertyRange(min = 0.0, max = 1.0) val pivotY: Float = 1f,
    @PropertyRange(min = 0.0) val radius: Float = DEFAULT_RADIUS,
    val startAngle: Float = DEFAULT_START_ANGLE,
    val spacing: Float = DEFAULT_SPACING,
    val primaryAtPivot: Boolean = true,
) {
    /** What is wrong with this cluster, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        if (pivotX !in 0f..1f || pivotY !in 0f..1f) add("pivotX and pivotY must be between 0 and 1")
        if (radius < 0f) add("radius must not be negative")
    }

    /** Where across a [width] dp element child [index]'s centre is, in dp from its left. */
    fun centerX(index: Int, width: Float): Float = pivotX * width + (angleOf(index)?.let { cos(it) * radius } ?: 0f)

    /** Where down a [height] dp element child [index]'s centre is, in dp from its top. */
    fun centerY(index: Int, height: Float): Float = pivotY * height + (angleOf(index)?.let { sin(it) * radius } ?: 0f)

    /** Child [index]'s angle on the arc, in radians, or null for the primary on the pivot. */
    private fun angleOf(index: Int): Float? {
        val step = if (primaryAtPivot) index - 1 else index
        return if (step < 0) null else (startAngle + spacing * step) * PI.toFloat() / HALF_TURN
    }

    private companion object {
        const val DEFAULT_RADIUS = 96f
        const val DEFAULT_START_ANGLE = 180f
        const val DEFAULT_SPACING = 45f
        const val HALF_TURN = 180f
    }
}

/** [children] of [element] placed by [cluster]: the primary on its pivot and the rest on its arc, in order. */
context(_: Composer)
internal fun BoxScope.ClusterChildren(element: CanvasElement, cluster: CanvasCluster, children: List<CanvasItem>, tree: CanvasTree) {
    val parentDensity = LocalDensity.current
    var index = 0
    for (item in children) {
        val child = item.element
        if (!tree.shows(child) || child.followArrow) continue
        val childDensity = item.scaler?.let(tree.box::densityOf) ?: parentDensity
        val ratio = if (parentDensity > 0f) childDensity / parentDensity else 1f
        val x = cluster.centerX(index, element.width) - child.width * ratio / 2f
        val y = cluster.centerY(index, element.height) - child.height * ratio / 2f
        index++
        key(child) {
            Box(Modifier.offset(x.dp, y.dp)) {
                WithItemScaler(item, tree) {
                    CanvasElementView(child, Modifier.size(child.width.dp, child.height.dp).testTag("canvas-element-${item.id}"), tree, tree.childrenOf(child))
                }
            }
        }
    }
}
