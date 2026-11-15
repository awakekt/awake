/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.game

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.PolledState
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.layout.Placeable
import com.awakekt.awake.compose.ui.node.ParentDataModifierNode
import com.awakekt.awake.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * Where a child of [WorldAnchorLayout] goes: a point in the layout's own pixels, and whether the
 * child shows. One instance is reused for every read, so a provider writes into it and allocates
 * nothing. [surfaceWidth] and [surfaceHeight] are the layout's; the layout sets them, and a test
 * of a provider passes its own.
 */
class AnchorPoint(surfaceWidth: Int = 0, surfaceHeight: Int = 0) {
    /** Pixels from the layout's left edge where the child's anchor sits. */
    var x: Float = 0f

    /** Pixels from the layout's top edge where the child's anchor sits. */
    var y: Float = 0f

    /** Whether the child shows. A hidden child is measured at no size and draws nothing. */
    var visible: Boolean = true

    /** Which point of the child sits on ([x], [y]): 0 its left or top edge, 0.5 its centre, 1 its right or bottom edge. */
    var pivotX: Float = 0.5f

    /** Vertical counterpart of [pivotX]. */
    var pivotY: Float = 1f

    /** Width of the layout the child is placed in, in pixels, for a provider that culls what falls outside it. */
    var surfaceWidth: Int = surfaceWidth
        internal set

    /** Height of the layout the child is placed in, in pixels. */
    var surfaceHeight: Int = surfaceHeight
        internal set

    /** Puts the point back to a visible child, bottom-centre on the origin, before a provider fills it. */
    internal fun reset(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        x = 0f
        y = 0f
        visible = true
        pivotX = 0.5f
        pivotY = 1f
    }
}

/**
 * Reports where a world-following child belongs. The layout asks while it places the child, once
 * for each frame the child is laid out, so the position can change every frame without the content
 * composing or measuring again for it. Measuring asks only whether the child shows: with
 * `ComposeHost.skipUnchangedMeasure` on, the host asks that again before each frame and measures
 * only when the answer changed, so a moving anchor that stays visible is placed and never measured.
 *
 * It must not allocate: it runs for every anchored child every frame. A provider that projects a
 * world position fills [AnchorPoint] from its camera; the layout knows nothing of scenes.
 */
fun interface AnchorProvider {
    /** Fills [into] with where the child goes, setting [AnchorPoint.visible] to false to hide it. */
    fun anchor(into: AnchorPoint)
}

/**
 * Anchors this child of a [WorldAnchorLayout] at the point [provider] reports each frame, instead
 * of stacking it. A child with no anchor is stacked at the layout's top-left.
 */
fun Modifier.worldAnchor(provider: AnchorProvider): Modifier = this then WorldAnchorElement(provider)

context(_: Composer)
/**
 * Lays its children out over a surface, each at a position read at placement time from its
 * [worldAnchor]: the shape of a nameplate, a bar over an entity or a line of damage text that
 * follows something in the world.
 *
 * Culling, distance fade and ranking are the provider's to decide; a provider that says "not
 * visible" gets its child measured at zero size and drawn as nothing. Positions are rounded to
 * whole pixels, which keeps text crisp as an anchor drifts. The layout fills the space it is given.

 */
fun WorldAnchorLayout(
    modifier: Modifier = Modifier,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    val policy = remember { WorldAnchorPolicy() }
    Layout(nodeType = WorldAnchorNodeType, modifier = modifier, measurePolicy = policy, content = content)
}

private object WorldAnchorNodeType

/**
 * Measures and places anchored children; one is kept per layout so measuring reuses its scratch.
 *
 * The placeables stay in `placeables` after placing, because a host that skips measuring places the
 * tree again from the last measure's block (#1512).
 */
private class WorldAnchorPolicy : MeasurePolicy {
    private val point = AnchorPoint()
    private var placeables = arrayOfNulls<Placeable>(INITIAL)

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val count = measurables.size
        if (placeables.size < count) placeables = arrayOfNulls(count)
        val free = Constraints.of(0, constraints.maxWidth, 0, constraints.maxHeight)
        val hidden = Constraints.of(0, 0, 0, 0)
        val width = constraints.constrainWidth(constraints.maxWidth)
        val height = constraints.constrainHeight(constraints.maxHeight)
        val placeables = placeables
        for (i in 0 until count) {
            val shown = measurables[i].anchorData()?.shown
            placeables[i] = measurables[i].measure(if (shown != null && !shown.on(width, height).value) hidden else free)
        }
        for (i in count until placeables.size) placeables[i] = null
        return layout(width, height) { place(measurables, count, width, height) }
    }

    private fun place(measurables: List<Measurable>, count: Int, width: Int, height: Int) {
        val placeables = placeables
        for (i in 0 until count) {
            val placeable = placeables[i] ?: continue
            placeAnchored(placeable, measurables[i].anchorProvider(), width, height)
        }
    }

    /** Puts [placeable] where [provider] says, at the top-left with none, and off the surface when it hides. */
    private fun placeAnchored(placeable: Placeable, provider: AnchorProvider?, width: Int, height: Int) {
        if (provider == null) {
            placeable.placeAt(0, 0)
            return
        }
        point.reset(width, height)
        provider.anchor(point)
        if (point.visible) {
            placeable.placeAt(
                (point.x - point.pivotX * placeable.width).roundToInt(),
                (point.y - point.pivotY * placeable.height).roundToInt(),
            )
        } else {
            placeable.placeAt(OFF_SCREEN, OFF_SCREEN)
        }
    }

    private companion object {
        const val INITIAL = 16

        /** Where a hidden child goes: far enough off any surface that nothing of it reaches one. */
        const val OFF_SCREEN = -1_000_000
    }
}

private fun Measurable.anchorData(): AnchorParentData? = parentData as? AnchorParentData

private fun Measurable.anchorProvider(): AnchorProvider? = anchorData()?.provider

private class AnchorParentData(val provider: AnchorProvider) {
    val shown = AnchorShown(provider)
}

/**
 * Whether [provider] shows its child on a surface of the size last given to [on], as a polled state:
 * a host that records what measuring read asks it again before the next frame, and measures again
 * only when the child appeared or hid. It reads the provider, which must not allocate.
 */
private class AnchorShown(private val provider: AnchorProvider) : PolledState<Boolean>() {
    private val point = AnchorPoint()
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    fun on(width: Int, height: Int): AnchorShown {
        surfaceWidth = width
        surfaceHeight = height
        return this
    }

    override fun read(): Boolean {
        point.reset(surfaceWidth, surfaceHeight)
        provider.anchor(point)
        return point.visible
    }
}

private class WorldAnchorElement(private val provider: AnchorProvider) : ModifierNodeElement<WorldAnchorNode>() {
    override fun create(): WorldAnchorNode = WorldAnchorNode(provider)

    override fun update(node: WorldAnchorNode) {
        node.provider = provider
    }
}

private class WorldAnchorNode(var provider: AnchorProvider) :
    Modifier.Node(),
    ParentDataModifierNode {
    // One holder per node, rewritten only when the provider changes, so the parent data read each frame is stable.
    private var data = AnchorParentData(provider)

    override fun modifyParentData(current: Any?): Any? {
        if (data.provider !== provider) data = AnchorParentData(provider)
        return data
    }

    override fun toString(): String = "worldAnchor"
}
