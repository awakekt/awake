/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.ui.focus.FocusRequester
import com.awakekt.awake.compose.ui.focus.focusRequester
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.node.LayoutNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ModifierNodeLifecycleTest {
    @Test
    fun equivalentElementsRetainTheNodeAndUpdateItsParameters() {
        TrackingElement.created.clear()
        val events = mutableListOf<String>()
        val layout = LayoutNode(emptyPolicy)

        layout.modifier = TrackingElement("first", events)
        val first = TrackingElement.created.single()

        layout.modifier = TrackingElement("second", events)

        assertSame(first, TrackingElement.created.single())
        assertEquals("second", first.value)
        assertEquals(listOf("update:first", "attach:first", "update:second"), events)
    }

    @Test
    fun removalDetachesTheRetainedNodeExactlyOnce() {
        TrackingElement.created.clear()
        val events = mutableListOf<String>()
        val layout = LayoutNode(emptyPolicy)

        layout.modifier = TrackingElement("value", events)
        val node = TrackingElement.created.single()
        layout.modifier = Modifier
        layout.modifier = Modifier

        assertEquals(listOf("update:value", "attach:value", "detach:value"), events)
        assertNull(node.layoutNode)
    }

    @Test
    fun replacingAnElementDetachesBeforeAttachingTheReplacement() {
        val events = mutableListOf<String>()
        val layout = LayoutNode(emptyPolicy)

        layout.modifier = TrackingElement("old", events)
        layout.modifier = OtherTrackingElement(events)

        assertEquals(
            listOf("update:old", "attach:old", "detach:old", "otherUpdate", "otherAttach"),
            events,
        )
    }

    @Test
    fun removingALayoutNodeDetachesItsModifierNodes() {
        TrackingElement.created.clear()
        val events = mutableListOf<String>()
        val parent = LayoutNode(emptyPolicy)
        val child = LayoutNode(emptyPolicy).also {
            it.modifier = TrackingElement("child", events)
        }

        parent.children.add(child)
        parent.children.truncateFrom(0)

        assertEquals(listOf("update:child", "attach:child", "detach:child"), events)
        assertNull(TrackingElement.created.single().layoutNode)
    }

    @Test
    fun focusRequesterClearsItsTargetWhenTheModifierIsRemoved() {
        val requester = FocusRequester()
        val layout = LayoutNode(emptyPolicy)

        layout.modifier = Modifier.focusRequester(requester)
        assertSame(layout, requester.node)

        layout.modifier = Modifier
        assertNull(requester.node)
    }

    private class TrackingElement(
        private val value: String,
        private val events: MutableList<String>,
    ) : ModifierNodeElement<TrackingNode>() {
        override fun create(): TrackingNode = TrackingNode(events).also { created += it }

        override fun update(node: TrackingNode) {
            node.value = value
            events += "update:$value"
        }

        companion object {
            val created = mutableListOf<TrackingNode>()
        }
    }

    private class TrackingNode(private val events: MutableList<String>) : Modifier.Node() {
        var value: String = ""

        override fun onAttach() {
            events += "attach:$value"
        }

        override fun onDetach() {
            events += "detach:$value"
        }
    }

    private class OtherTrackingElement(
        private val events: MutableList<String>,
    ) : ModifierNodeElement<OtherTrackingNode>() {
        override fun create(): OtherTrackingNode = OtherTrackingNode(events)

        override fun update(node: OtherTrackingNode) {
            events += "otherUpdate"
        }
    }

    private class OtherTrackingNode(private val events: MutableList<String>) : Modifier.Node() {
        override fun onAttach() {
            events += "otherAttach"
        }
    }

    private companion object {
        val emptyPolicy = object : MeasurePolicy {
            override fun com.awakekt.awake.compose.ui.layout.MeasureScope.measure(
                measurables: List<com.awakekt.awake.compose.ui.layout.Measurable>,
                constraints: com.awakekt.awake.compose.ui.unit.Constraints,
            ) = layout(constraints.minWidth, constraints.minHeight) {}
        }
    }
}
