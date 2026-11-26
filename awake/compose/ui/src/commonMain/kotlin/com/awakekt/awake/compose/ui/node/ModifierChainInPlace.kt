/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.node

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.input.nestedscroll.NestedScrollNode
import com.awakekt.awake.core.input.CursorImage
import com.awakekt.awake.core.input.PointerCursor

/**
 * Re-sets [owner]'s modifier without rebuilding its chain when the new modifier has the old one's
 * shape: every element a node element of the class retained at its position, and as many of them.
 *
 * A composable that hands a new modifier each frame (a mark that moves, a panel, an anchored item)
 * almost always hands the same links with new values. The retained nodes take the values, so the
 * links, the per-kind lists and the depths built from them all hold; only what a node reports from
 * its values is read again: parent data, z-index, the cursor request and the pointer hit margin.
 * A full rebuild cleared and refilled sixteen lists and boxed its fold's locals, per node per frame.
 *
 * A chain holding a plain element or a nested-scroll link is always rebuilt: a plain element is a
 * new object each time and is held in the lists itself, and a nested-scroll link's connection can
 * change with an update.
 *
 * Walks the modifier as its own fold operation, with its state in fields, so a walk allocates nothing.
 */
internal class ModifierChainInPlace(private val owner: LayoutNode) : (Unit, Modifier.Element) -> Unit {
    private var checking = false
    private var matches = false
    private var index = 0
    private var layoutLinks = 0
    private var parentData: Any? = null
    private var zIndex = 0f
    private var cursor: PointerCursor? = null
    private var cursorImage: CursorImage? = null
    private var cursorDepth = 0
    private var hitMargin = 0
    private var anyPointer = false
    private var drawChanged = false

    /** Updates the chain in place and returns true, or returns false when [modifier] needs a rebuild. */
    fun update(modifier: Modifier): Boolean {
        if (!hasSameShape(modifier)) return false
        checking = false
        index = 0
        layoutLinks = 0
        parentData = null
        zIndex = 0f
        cursor = null
        cursorImage = null
        cursorDepth = 0
        hitMargin = 0
        anyPointer = false
        drawChanged = false
        modifier.foldIn(Unit, this)
        val zChanged = owner.zIndex != zIndex
        owner.parentData = parentData
        owner.zIndex = zIndex
        owner.pointerCursor = cursor
        owner.pointerCursorImage = cursorImage
        owner.pointerCursorDepth = cursorDepth
        owner.maxPointerHitMargin = if (anyPointer) hitMargin else 0
        parentData = null
        if (zChanged) owner.parent?.markDrawDirty()
        if (drawChanged) owner.markDrawDirty()
        return true
    }

    private fun hasSameShape(modifier: Modifier): Boolean {
        val retained = owner.retainedModifierNodes.size
        if (retained == 0) return false
        checking = true
        matches = true
        index = 0
        modifier.foldIn(Unit, this)
        return matches && index == retained
    }

    override fun invoke(unit: Unit, element: Modifier.Element) {
        if (checking) check(element) else apply(element)
        index += 1
    }

    private fun check(element: Modifier.Element) {
        if (!matches) return
        val retained = owner.retainedModifierNodes.getOrNull(index)
        matches = element is ModifierNodeElement<*> &&
            retained != null &&
            retained.node !is NestedScrollNode &&
            retained.elementClass.isInstance(element)
    }

    // The same reads, in the same order, as the full rebuild's fold.
    private fun apply(element: Modifier.Element) {
        val retained = checkNotNull(owner.retainedModifierNodes[index])
        val node = retained.node
        if (node is DrawModifierNode && retained.lastElement !== element && retained.lastElement != element) {
            retained.lastElement = element
            drawChanged = true
        }
        (element as ModifierNodeElement<*>).updateUnchecked(node)
        val outside = layoutLinks
        if (node is LayoutModifierNode) layoutLinks += 1
        if (node is PointerInputNode) {
            hitMargin = if (anyPointer) maxOf(hitMargin, node.hitMarginPx) else node.hitMarginPx
            anyPointer = true
        }
        if (node is ZIndexModifierNode) zIndex = node.modifyZIndex(zIndex)
        cursor = node.cursorOrKeep(cursor)
        cursorImage = node.cursorImageOrKeep(cursorImage)
        cursorDepth = node.cursorDepthOrKeep(cursorDepth, outside)
        if (node is NodeAttachedModifierNode) node.onAttachedTo(owner)
        if (node is ParentDataModifierNode) parentData = node.modifyParentData(parentData)
    }
}
