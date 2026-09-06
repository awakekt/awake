/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.ui.layout

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.Composition
import com.awakekt.awake.compose.runtime.ProvidedLocals
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.node
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.node.EmptyMeasurePolicy
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.node.LayoutNodeApplier
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.platform.LocalFontScale
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Density
import com.awakekt.awake.compose.ui.unit.Dp

/**
 * Scope provided to [SubcomposeLayout]'s measure policy, allowing children to be composed and
 * measured dynamically based on incoming constraints.
 */
interface SubcomposeMeasureScope : MeasureScope {
    /**
     * Composes the given [content] under [slotId] and returns its measured children.
     */
    fun subcompose(
        slotId: Any?,
        content: context(Composer) () -> Unit,
    ): List<Measurable>
}

/** Policy governing which unused subcomposition slots are retained across frames. */
fun interface SubcomposeSlotReusePolicy {
    fun getSlotsToRetain(slotIds: Set<Any?>): Set<Any?>

    companion object {
        val Default = SubcomposeSlotReusePolicy { it }
    }
}

/**
 * Retains subcompositions across frames for [SubcomposeLayout].
 */
class SubcomposeLayoutState(
    val slotReusePolicy: SubcomposeSlotReusePolicy = SubcomposeSlotReusePolicy.Default,
) {
    internal class SlotRecord(
        val slotId: Any?,
        val rootNode: LayoutNode,
        val composition: Composition,
    )

    private val slotMap = LinkedHashMap<Any?, SlotRecord>()
    private val activeSlots = LinkedHashSet<Any?>()
    internal var parentLocals: ProvidedLocals? = null
    internal var density: Float = 1f
    internal var fontScale: Float = 1f
    internal var layoutDirection: com.awakekt.awake.compose.ui.unit.LayoutDirection =
        com.awakekt.awake.compose.ui.unit.LayoutDirection.Ltr

    internal fun subcompose(
        slotId: Any?,
        content: context(Composer) () -> Unit,
    ): List<Measurable> {
        activeSlots.add(slotId)
        var record = slotMap[slotId]
        if (record == null) {
            val root = LayoutNode(EmptyMeasurePolicy, density, fontScale, layoutDirection)
            val applier = LayoutNodeApplier(root)
            val composition = Composition(applier, root, parentLocals)
            record = SlotRecord(slotId, root, composition)
            slotMap[slotId] = record
        } else {
            record.rootNode.density = density
            record.rootNode.fontScale = fontScale
            record.rootNode.layoutDirection = layoutDirection
        }
        record.composition.reconcile(content)
        return record.rootNode.children.asList()
    }

    internal fun collectActiveChildren(): List<LayoutNode> {
        val result = mutableListOf<LayoutNode>()
        for (slotId in activeSlots) {
            val record = slotMap[slotId] ?: continue
            for (i in 0 until record.rootNode.children.size) {
                result.add(record.rootNode.children[i])
            }
        }
        return result
    }

    internal fun finishMeasurePass() {
        val retained = slotReusePolicy.getSlotsToRetain(activeSlots)
        val iterator = slotMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in activeSlots && entry.key !in retained) {
                iterator.remove()
            }
        }
        activeSlots.clear()
    }

    fun dispose() {
        slotMap.clear()
        activeSlots.clear()
    }
}

private object SubcomposeLayoutNodeType

/**
 * Declares a layout whose children are composed during measurement rather than during the root
 * composition pass.
 */
context(composer: Composer)
fun SubcomposeLayout(
    modifier: Modifier = Modifier,
    state: SubcomposeLayoutState = remember { SubcomposeLayoutState() },
    measurePolicy: SubcomposeMeasureScope.(Constraints) -> MeasureResult,
) {
    val density = LocalDensity.current
    val fontScale = LocalFontScale.current
    val layoutDirection = com.awakekt.awake.compose.ui.unit.LocalLayoutDirection.current
    val locals = composer.localsSnapshot

    state.parentLocals = locals
    state.density = density
    state.fontScale = fontScale
    state.layoutDirection = layoutDirection

    composer.node(
        type = SubcomposeLayoutNodeType,
        update = { node ->
            node as LayoutNode
            node.density = density
            node.fontScale = fontScale
            node.layoutDirection = layoutDirection
            node.modifier = modifier
            node.measurePolicy = SubcomposeMeasurePolicy(state, measurePolicy, node)
        },
    )
}

/** Overload with default remembered state. */
context(composer: Composer)
fun SubcomposeLayout(
    modifier: Modifier = Modifier,
    measurePolicy: SubcomposeMeasureScope.(Constraints) -> MeasureResult,
) {
    SubcomposeLayout(
        modifier = modifier,
        state = remember { SubcomposeLayoutState() },
        measurePolicy = measurePolicy,
    )
}

private class SubcomposeMeasurePolicy(
    private val state: SubcomposeLayoutState,
    private val measureBlock: SubcomposeMeasureScope.(Constraints) -> MeasureResult,
    private val layoutNode: LayoutNode,
) : MeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val subcomposeScope = object : SubcomposeMeasureScope, MeasureScope by this {
            override fun subcompose(
                slotId: Any?,
                content: context(Composer) () -> Unit,
            ): List<Measurable> = state.subcompose(slotId, content)
        }

        val result = subcomposeScope.measureBlock(constraints)

        // Sync active subcomposed nodes into the layout tree for coordinates, hit testing, and paint.
        val activeChildren = state.collectActiveChildren()
        layoutNode.children.setFrom(activeChildren)
        state.finishMeasurePass()

        return result
    }
}
