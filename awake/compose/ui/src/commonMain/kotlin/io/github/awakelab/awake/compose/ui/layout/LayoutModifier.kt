/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.layout

import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.node.LayoutModifierNode
import io.github.awakelab.awake.compose.ui.node.ParentDataModifierNode
import io.github.awakelab.awake.compose.ui.unit.Constraints

/**
 * Measures and places the content however [measure] says.
 *
 * The escape hatch under `padding`, `size` and the rest: they are all this with a fixed body, and a
 * caller who needs a rule none of them express writes it here rather than waiting for one.
 *
 * Compose's own signature. The receiver is a [MeasureScope], so `layout(w, h) { placeAt() }` reads
 * the same as it does inside a `MeasurePolicy`.
 */
fun Modifier.layout(
    measure: MeasureScope.(measurable: Measurable, constraints: Constraints) -> MeasureResult,
): Modifier = this then LayoutModifierElement(measure)

/**
 * Tags this node so a parent's [MeasurePolicy] can tell it apart from its siblings.
 *
 * The alternative in a custom layout is positional -- "the second measurable is the label" -- which
 * silently means something else the moment a caller reorders or conditionally omits a child.
 */
fun Modifier.layoutId(id: Any): Modifier = this then LayoutIdElement(id)

/** The id set by [layoutId], or null. Read from a measure policy via `measurable.parentData`. */
val Measurable.layoutId: Any?
    get() = (parentData as? LayoutIdParentData)?.id

private class LayoutModifierElement(
    private val measureBlock: MeasureScope.(Measurable, Constraints) -> MeasureResult,
) : ModifierNodeElement<LayoutModifierNodeImpl>() {
    override fun create(): LayoutModifierNodeImpl = LayoutModifierNodeImpl()

    override fun update(node: LayoutModifierNodeImpl) {
        node.measureBlock = measureBlock
    }
}

private class LayoutModifierNodeImpl :
    Modifier.Node(),
    LayoutModifierNode {
    lateinit var measureBlock: MeasureScope.(Measurable, Constraints) -> MeasureResult
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult =
        measureBlock(measurable, constraints)

    override fun toString(): String = "layout()"
}

private class LayoutIdParentData(val id: Any)

private class LayoutIdElement(
    private val id: Any,
) : ModifierNodeElement<LayoutIdNode>() {
    override fun create(): LayoutIdNode = LayoutIdNode()

    override fun update(node: LayoutIdNode) {
        node.id = id
    }
}

private class LayoutIdNode :
    Modifier.Node(),
    ParentDataModifierNode {
    lateinit var id: Any
    override fun modifyParentData(current: Any?): Any? = LayoutIdParentData(id)

    override fun toString(): String = "layoutId($id)"
}
