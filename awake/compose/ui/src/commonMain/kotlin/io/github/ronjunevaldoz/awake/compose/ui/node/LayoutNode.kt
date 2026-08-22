// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.node

import io.github.ronjunevaldoz.awake.compose.runtime.RememberHolder
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.layout.IntrinsicMeasurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.LayoutStats
import io.github.ronjunevaldoz.awake.compose.ui.layout.Measurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasurePolicy
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureResult
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureScope
import io.github.ronjunevaldoz.awake.compose.ui.layout.Placeable
import io.github.ronjunevaldoz.awake.compose.ui.layout.PlacementScope
import io.github.ronjunevaldoz.awake.compose.ui.layout.onPlaced
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints

/**
 * One node of the retained layout tree: a [MeasurePolicy], a [Modifier] chain, its children, and
 * the size and position the last layout pass gave it.
 *
 * The node is its own [Placeable], so measuring a child hands the parent the child itself rather
 * than a wrapper. The chain's own placeables and scopes are built when [modifier] changes, never
 * per pass, so measuring allocates nothing here.
 */
class LayoutNode(
    var measurePolicy: MeasurePolicy,
    var density: Float = 1f,
    var fontScale: Float = 1f,
) : Placeable(),
    Measurable,
    RememberHolder {

    /** Children this node's [measurePolicy] lays out. */
    val children: NodeChildren = NodeChildren()

    /** `remember` slots declared in this node's content. Created once with the node, never per pass. */
    override val rememberSlots: MutableList<Any?> = ArrayList()

    /**
     * Overlays declared inside this node -- popup, dialog, tooltip.
     *
     * Separate from [children] because a layer is measured against the viewport, not against this
     * node's constraints, so the policy must never see it. It stays attached here rather than
     * hoisted to the root because an anchored popup anchors to the node that declared it. See
     * `docs/reference/compose-engine/07-overlay-layering.md`.
     */
    val layers: NodeChildren = NodeChildren()

    /**
     * True for a layer that captures focus: a dialog, or a menu that should not let Tab escape it.
     *
     * Set by `Layer(modal = true)` rather than inferred from [LayerKind], because modality is a
     * property of the individual overlay -- a tooltip is never modal, but a popup may or may not be,
     * and guessing from the kind would be wrong half the time.
     */
    var isModal: Boolean = false

    /** Identity for reconciliation: what this node declared itself as, and any explicit `key(...)`. */
    var nodeType: Any? = null
        internal set

    var nodeKey: Any? = null
        internal set

    var modifier: Modifier = Modifier
        set(value) {
            field = value
            rebuildChain()
        }

    /** Derived from the [ParentDataModifierNode]s in [modifier] -- `weight()`, `align()`. */
    override var parentData: Any? = null
        private set

    /** Position in the tree's coordinate space, resolved by [resolveAbsolutePositions]. */
    var absoluteX: Int = 0
        private set

    var absoluteY: Int = 0
        private set

    private var links: Array<ChainLink> = emptyArray()

    private val layoutModifiers = ArrayList<LayoutModifierNode>()
    private val drawNodes = ArrayList<DrawModifierNode>()
    private val pointerNodes = ArrayList<PointerInputNode>()
    private val semanticsNodes = ArrayList<SemanticsModifierNode>()
    private val placedNodes = ArrayList<OnPlacedModifierNode>()
    private val focusNodes = ArrayList<FocusTargetNode>()
    private val scrollNodes = ArrayList<ScrollableNode>()
    private val textInputNodes = ArrayList<TextInputNode>()
    private val drawDepthScratch = ArrayList<Int>()
    private val pointerDepthScratch = ArrayList<Int>()

    // The four link lists below are the node's own scratch lists, published rather than copied.
    // Copying cost four ArrayLists per node per frame. The contract that makes it safe is the frame
    // order: they are refilled during reconcile and read during layout, paint and input dispatch,
    // never across a reconcile. Same tradeoff the reused MeasureResult already carries -- do not
    // retain one past a pass.

    /** Draw links, outermost first. Rebuilt with the chain, never per frame. */
    internal var drawModifiers: List<DrawModifierNode> = emptyList()
        private set

    /**
     * How many layout links sit outside each draw link, index-aligned with [drawModifiers].
     *
     * Without this a draw link has no idea where it sits in the chain, and every one of them paints
     * the node's full bounds -- which makes `padding().background()` and `background().padding()`
     * produce identical output. Order being silently *meaningless* is worse than Compose's order
     * being silently meaningful.
     */
    internal var drawDepths: IntArray = EMPTY_DEPTHS
        private set

    /** Pointer links, outermost first. Rebuilt with the chain, never per frame. */
    internal var pointerInputs: List<PointerInputNode> = emptyList()
        private set

    /**
     * How many layout links sit outside each pointer link, index-aligned with [pointerInputs].
     *
     * The hit-target half of [drawDepths]: a `clickable` declared inside a `padding` is clickable on
     * the padded box, not the node's outer bounds. Compose behaves this way and it is what makes
     * `padding().clickable()` differ from `clickable().padding()`.
     */
    internal var pointerDepths: IntArray = EMPTY_DEPTHS
        private set

    /** Semantics links, outermost first. Rebuilt with the chain, never per frame. */
    internal var semanticsModifiers: List<SemanticsModifierNode> = emptyList()
        private set

    /** Focus links, outermost first. Rebuilt with the chain, never per frame. */
    internal var focusTargets: List<FocusTargetNode> = emptyList()
        private set

    /** Scroll links, outermost first. Rebuilt with the chain, never per frame. */
    internal var scrollables: List<ScrollableNode> = emptyList()
        private set

    /** Text-input links, outermost first. Rebuilt with the chain, never per frame. */
    internal var textInputs: List<TextInputNode> = emptyList()
        private set

    private var outermost: Placeable? = null

    private val contentScope = NodeMeasureScope()
    private val contentPlaceable = ModifierPlaceable()

    private val contentMeasurable = object : Measurable {
        override val parentData: Any? get() = this@LayoutNode.parentData

        // The policy answers for its own children, which is what makes a Column sum heights where
        // a Box maxes them.
        override fun minIntrinsicWidth(height: Int) =
            with(measurePolicy) { contentScope.minIntrinsicWidth(children.asList(), height) }

        override fun maxIntrinsicWidth(height: Int) =
            with(measurePolicy) { contentScope.maxIntrinsicWidth(children.asList(), height) }

        override fun minIntrinsicHeight(width: Int) =
            with(measurePolicy) { contentScope.minIntrinsicHeight(children.asList(), width) }

        override fun maxIntrinsicHeight(width: Int) =
            with(measurePolicy) { contentScope.maxIntrinsicHeight(children.asList(), width) }

        override fun measure(constraints: Constraints): Placeable {
            val result = with(measurePolicy) { contentScope.measure(children.asList(), constraints) }
            contentPlaceable.update(result)
            return contentPlaceable
        }
    }

    override fun minIntrinsicWidth(height: Int): Int = intrinsic { minIntrinsicWidth(height) }

    override fun maxIntrinsicWidth(height: Int): Int = intrinsic { maxIntrinsicWidth(height) }

    override fun minIntrinsicHeight(width: Int): Int = intrinsic { minIntrinsicHeight(width) }

    override fun maxIntrinsicHeight(width: Int): Int = intrinsic { maxIntrinsicHeight(width) }

    /** Enters the chain at its outermost link, or the policy directly when there is none. */
    private inline fun intrinsic(query: IntrinsicMeasurable.() -> Int): Int {
        LayoutStats.recordIntrinsicQuery()
        return if (links.isEmpty()) contentMeasurable.query() else links[0].query()
    }

    override fun measure(constraints: Constraints): Placeable {
        val outer = if (links.isEmpty()) {
            contentMeasurable.measure(constraints)
        } else {
            links[0].measure(constraints)
        }
        outermost = outer
        setMeasuredSize(outer.width, outer.height)
        return this
    }

    /** Runs the placement the chain and the policy deferred. */
    override fun onPlaced() {
        outermost?.placeAt(0, 0)
    }

    /**
     * Turns the relative positions placement produced into tree-space coordinates.
     *
     * Separate from placement on purpose: a policy positions children relative to itself and never
     * needs to know where it sits, which is what lets a subtree be measured and placed in isolation.
     */
    fun resolveAbsolutePositions(parentAbsoluteX: Int = 0, parentAbsoluteY: Int = 0) {
        absoluteX = parentAbsoluteX + x
        absoluteY = parentAbsoluteY + y
        // Children are placed relative to the content box, which the chain may have inset -- a
        // padding link offsets everything inside it.
        var contentX = absoluteX
        var contentY = absoluteY
        for (i in links.indices) {
            contentX += links[i].placeable.x
            contentY += links[i].placeable.y
        }
        contentX += contentPlaceable.x
        contentY += contentPlaceable.y
        for (i in placedNodes.indices) {
            placedNodes[i].onPlaced(absoluteX, absoluteY, width, height)
        }
        for (i in children.indices) {
            children[i].resolveAbsolutePositions(contentX, contentY)
        }
        // Layers resolve against this node too -- an anchored popup needs its declaring node's
        // position -- but their size comes from the viewport, not from this node's constraints.
        for (i in layers.indices) {
            layers[i].resolveAbsolutePositions(absoluteX, absoluteY)
        }
    }

    /**
     * The rect a draw link at [depth] layout links deep should paint into.
     *
     * Walks inward one layout link at a time, taking the offset each link placed its content at and
     * the size that content reported. Writes into [out] rather than returning, because painting runs
     * per link per node per frame.
     */
    internal fun drawBoundsAt(depth: Int, out: IntArray) {
        var dx = 0
        var dy = 0
        var w = width
        var h = height
        for (i in 1..depth) {
            val inner = if (i < links.size) links[i].placeable else contentPlaceable
            dx += inner.x
            dy += inner.y
            w = inner.width
            h = inner.height
        }
        out[0] = dx
        out[1] = dy
        out[2] = w
        out[3] = h
    }

    private fun rebuildChain() {
        // Cleared and refilled rather than reallocated: rebuildChain runs per node per frame and
        // is never reentrant, so one set of scratch lists per node is safe and four fewer objects.
        layoutModifiers.clear()
        drawNodes.clear()
        pointerNodes.clear()
        semanticsNodes.clear()
        placedNodes.clear()
        focusNodes.clear()
        scrollNodes.clear()
        textInputNodes.clear()
        drawDepthScratch.clear()
        pointerDepthScratch.clear()
        var data: Any? = null
        modifier.foldIn(Unit) { _, element ->
            if (element is LayoutModifierNode) layoutModifiers += element
            if (element is DrawModifierNode) {
                drawNodes += element
                drawDepthScratch += layoutModifiers.size
            }
            if (element is PointerInputNode) {
                pointerNodes += element
                pointerDepthScratch += layoutModifiers.size
            }
            if (element is SemanticsModifierNode) semanticsNodes += element
            if (element is OnPlacedModifierNode) placedNodes += element
            if (element is FocusTargetNode) focusNodes += element
            if (element is ScrollableNode) scrollNodes += element
            if (element is TextInputNode) textInputNodes += element
            if (element is NodeAttachedModifierNode) element.onAttachedTo(this)
            if (element is ParentDataModifierNode) data = element.modifyParentData(data)
        }
        parentData = data
        drawModifiers = drawNodes
        drawDepths = drawDepthScratch.snapshotInto(drawDepths)
        pointerInputs = pointerNodes
        pointerDepths = pointerDepthScratch.snapshotInto(pointerDepths)
        semanticsModifiers = semanticsNodes
        focusTargets = focusNodes
        scrollables = scrollNodes
        textInputs = textInputNodes
        // A chain of the same length is re-pointed rather than rebuilt. The modifier objects are
        // new every frame -- nothing memoizes them until Stage 2's `remember` -- and rebuilding
        // meant a ChainLink, a placeable, a scope and a result object per link per node per frame.
        // That was 83% of the frame's allocation.
        if (links.size == layoutModifiers.size) {
            for (i in links.indices) links[i].node = layoutModifiers[i]
        } else {
            links = Array(layoutModifiers.size) { ChainLink(it, layoutModifiers[it]) }
        }
    }

    /**
     * One link of the modifier chain, presented to the link outside it as a [Measurable].
     *
     * Each link owns its own scope, because [NodeMeasureScope.layout] hands back a reused result
     * object -- sharing one across links would have every link overwrite the previous one's size.
     */
    private inner class ChainLink(
        private val index: Int,
        var node: LayoutModifierNode,
    ) : Measurable {
        val placeable = ModifierPlaceable()
        private val scope = NodeMeasureScope()

        override val parentData: Any? get() = this@LayoutNode.parentData

        private val inner: Measurable
            get() = if (index + 1 < links.size) links[index + 1] else contentMeasurable

        override fun minIntrinsicWidth(height: Int) =
            with(node) { scope.minIntrinsicWidth(inner, height) }

        override fun maxIntrinsicWidth(height: Int) =
            with(node) { scope.maxIntrinsicWidth(inner, height) }

        override fun minIntrinsicHeight(width: Int) =
            with(node) { scope.minIntrinsicHeight(inner, width) }

        override fun maxIntrinsicHeight(width: Int) =
            with(node) { scope.maxIntrinsicHeight(inner, width) }

        override fun measure(constraints: Constraints): Placeable {
            val inner: Measurable =
                if (index + 1 < links.size) links[index + 1] else contentMeasurable
            placeable.update(with(node) { scope.measure(inner, constraints) })
            return placeable
        }
    }

    private inner class NodeMeasureScope :
        MeasureScope,
        PlacementScope {
        override val density: Float get() = this@LayoutNode.density
        override val fontScale: Float get() = this@LayoutNode.fontScale

        // Reused across passes -- `layout()` is called once per scope per pass, and a fresh result
        // object each time would be one allocation per link per node per frame.
        private val result = MutableMeasureResult()

        override fun layout(
            width: Int,
            height: Int,
            place: PlacementScope.() -> Unit,
        ): MeasureResult = result.apply {
            this.width = width
            this.height = height
            this.place = place
            this.scope = this@NodeMeasureScope
        }
    }

    private class MutableMeasureResult : MeasureResult {
        override var width: Int = 0
        override var height: Int = 0
        var place: (PlacementScope.() -> Unit)? = null
        var scope: PlacementScope? = null

        override fun placeChildren() {
            val block = place ?: return
            val placementScope = scope ?: return
            placementScope.block()
        }
    }

    private class ModifierPlaceable : Placeable() {
        private var result: MeasureResult? = null

        fun update(result: MeasureResult) {
            this.result = result
            setMeasuredSize(result.width, result.height)
        }

        override fun onPlaced() {
            result?.placeChildren()
        }
    }
}

private val EMPTY_DEPTHS = IntArray(0)

/**
 * Copies into [existing] when it is already the right length, allocating only when the shape changed.
 *
 * `toIntArray()` here cost a fresh array per node per frame -- the same trap the chain links avoid by
 * being re-pointed rather than rebuilt.
 */
private fun List<Int>.snapshotInto(existing: IntArray): IntArray {
    if (isEmpty()) return EMPTY_DEPTHS
    val target = if (existing.size == size) existing else IntArray(size)
    for (i in indices) target[i] = this[i]
    return target
}
