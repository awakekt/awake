/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.DefaultFontSize
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.platform.LocalFont
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

private object SizedType

private val stackPolicy = MeasurePolicy { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    val width = placeables.maxOfOrNull { it.width } ?: 0
    val height = placeables.maxOfOrNull { it.height } ?: 0
    layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
        placeables.forEach { it.placeAt(0, 0) }
    }
}

context(_: Composer)
private fun sized(size: Int) =
    Layout(SizedType, modifier = Modifier.size(size.dp), measurePolicy = stackPolicy)

class CompositionLocalsTest {

    @Test
    fun densityConvertsDpToPixelsAtMeasureTime() {
        val root = LayoutNode(stackPolicy)
        composeInto(root) {
            CompositionLocalProvider(LocalDensity, 2f) { sized(10) }
        }
        root.measure(Constraints.of(0, 500, 0, 500))

        assertEquals(20, root.children[0].width, "10.dp at density 2")
    }

    @Test
    fun aProvidedDensityAppliesOnlyToItsSubtree() {
        // ui-core reads density from a global, so it cannot express this at all -- which is what a
        // preview or a 2x snapshot test needs.
        val root = LayoutNode(stackPolicy)
        composeInto(root) {
            sized(10)
            CompositionLocalProvider(LocalDensity, 3f) { sized(10) }
        }
        root.measure(Constraints.of(0, 500, 0, 500))

        assertEquals(10, root.children[0].width, "outside the provider, density is the default 1")
        assertEquals(30, root.children[1].width)
    }

    @Test
    fun aChangedDensityReachesAReusedNode() {
        // The node survives the second pass; its density must not.
        val root = LayoutNode(stackPolicy)
        composeInto(root) { CompositionLocalProvider(LocalDensity, 1f) { sized(10) } }
        val node = root.children[0]

        composeInto(root) { CompositionLocalProvider(LocalDensity, 4f) { sized(10) } }
        root.measure(Constraints.of(0, 500, 0, 500))

        assertEquals(node, root.children[0], "same node")
        assertEquals(40, node.width)
    }

    @Test
    fun theHostsDensityReachesEveryNodeNotJustTheRoot() {
        // It did not. The constructor hands density to the root LayoutNode, but every other node
        // takes it from the local, which nothing provided -- so the whole tree below the root laid
        // out at 1x and a HiDPI display rendered the UI half-size with nothing reporting it.
        val host = ComposeHost(density = 2f)

        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 200)) { sized(10) }

        assertEquals(20, host.root.children[0].width, "10.dp at the host's density 2")
    }

    @Test
    fun densitySetAfterConstructionStillReachesEveryNode() {
        // A real display's scale is often unknown until its window exists, which is after
        // ComposeHost is constructed -- e.g. SceneAppLifecycleRuntime.uiHost's density is set
        // from the first AppFrame, not the constructor. Without ComposeHost.density's setter
        // also writing root.density, this assignment would update the composition local's
        // default but never reach an already-built root LayoutNode.
        val host = ComposeHost()
        host.density = 3f

        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 200)) { sized(10) }

        assertEquals(30, host.root.children[0].width, "10.dp at a density set after construction")
    }

    @Test
    fun textLocalsCarryTheirDefaults() {
        // The two locals that reach measurement. Text cannot be measured without them, so they
        // resolve to something usable before any theme provides a value.
        var styleSize: Any? = null
        var fontName: String? = null
        composeInto(LayoutNode(stackPolicy)) {
            styleSize = LocalTextStyle.current.size
            fontName = LocalFont.current::class.simpleName
        }

        assertEquals(DefaultFontSize, styleSize, "a concrete size, so text measures with no theme")
        assertEquals(true, fontName != null)
    }
}
