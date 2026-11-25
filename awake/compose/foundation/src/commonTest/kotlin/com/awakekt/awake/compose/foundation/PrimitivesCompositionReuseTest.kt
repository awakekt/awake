/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.PasswordMask
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.FilterQuality
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/**
 * Verifies that policy and state reuse in [Box], [Row], [Column], [Image], and [BasicTextField]
 * never serves stale output when any input changes between frames.
 */
class PrimitivesCompositionReuseTest {

    private fun byTag(nodes: List<SemanticsNode>, tag: String): SemanticsNode =
        nodes.firstNotNullOfOrNull { findTag(it, tag) } ?: error("missing tag '$tag'")

    private fun findTag(node: SemanticsNode, tag: String): SemanticsNode? =
        if (node.testTag == tag) node else node.children.firstNotNullOfOrNull { findTag(it, tag) }

    @Test
    fun boxRowAndColumnReusePolicyWhenUnchangedAndUpdateWhenChanged() {
        val host = ComposeHost()
        var boxAlign: Alignment = Alignment.TopStart
        var rowArr: Arrangement.Horizontal = Arrangement.spacedByHorizontal(4.dp)
        var colArr: Arrangement.Vertical = Arrangement.spacedBy(4.dp)

        fun render() = host.frame(FrameInput(200, 200)) {
            Column(Modifier.size(200.dp), verticalArrangement = colArr) {
                Box(Modifier.size(100.dp, 40.dp), contentAlignment = boxAlign) {
                    Box(Modifier.size(20.dp).testTag("box-child"))
                }
                Row(Modifier.size(100.dp, 40.dp), horizontalArrangement = rowArr) {
                    Box(Modifier.size(20.dp).testTag("row-a"))
                    Box(Modifier.size(20.dp).testTag("row-b"))
                }
            }
        }

        val first = render()
        val colNode = host.root.children[0]
        val boxNode = colNode.children[0]
        val rowNode = colNode.children[1]
        val colPolicy = colNode.measurePolicy
        val boxPolicy = boxNode.measurePolicy
        val rowPolicy = rowNode.measurePolicy

        render()
        assertSame(colPolicy, colNode.measurePolicy)
        assertSame(boxPolicy, boxNode.measurePolicy)
        assertSame(rowPolicy, rowNode.measurePolicy)

        boxAlign = Alignment.BottomEnd
        rowArr = Arrangement.spacedByHorizontal(12.dp)
        colArr = Arrangement.spacedBy(20.dp)
        val second = render()

        assertNotEquals(byTag(first.semantics, "box-child").x, byTag(second.semantics, "box-child").x)
        assertNotEquals(byTag(first.semantics, "row-b").x, byTag(second.semantics, "row-b").x)
        assertNotEquals(byTag(first.semantics, "row-a").y, byTag(second.semantics, "row-a").y)
    }

    @Test
    fun imageUpdatesWhenAnyInputChanges() {
        val bitmapA = ImageBitmap(16, 16, ByteArray(16 * 16 * 4) { 127 })
        val bitmapB = ImageBitmap(32, 32, ByteArray(32 * 32 * 4) { 64 })
        val host = ComposeHost()
        var bitmap = bitmapA
        var description: String? = "first"
        var scale = ContentScale.Fit
        var tint = Color.White
        var filter = FilterQuality.Low
        var modifier: Modifier = Modifier.size(40.dp, 20.dp)

        fun render() = host.frame(FrameInput(100, 100)) {
            Image(bitmap, description, modifier, scale, tint, filter)
        }

        val initial = render().primitives.toList()
        val initialPolicy = host.root.children[0].measurePolicy
        val initialModifier = host.root.children[0].modifier
        render()
        assertSame(initialPolicy, host.root.children[0].measurePolicy)
        assertSame(initialModifier, host.root.children[0].modifier)

        description = "second"
        assertEquals("second", render().semantics.single().label)

        tint = Color(1f, 0f, 0f, 1f)
        assertNotEquals(initial, render().primitives.toList())

        scale = ContentScale.FillBounds
        val afterScale = render().primitives.toList()
        filter = FilterQuality.None
        assertNotEquals(afterScale, render().primitives.toList())

        bitmap = bitmapB
        val afterBitmap = render().primitives.toList()
        modifier = Modifier.padding(4.dp).size(40.dp, 20.dp)
        assertNotEquals(afterBitmap, render().primitives.toList())
    }

    @Test
    fun basicTextFieldUpdatesWhenAnyInputChanges() {
        val host = ComposeHost()
        var state = TextFieldState("hello")
        var style = TextStyle(size = 12.sp, color = Color.White)
        var mask: Char? = null
        var enabled = true
        var modifier: Modifier = Modifier.size(120.dp, 24.dp).testTag("input")

        fun render() = host.frame(FrameInput(200, 60)) {
            BasicTextField(state, modifier, style = style, mask = mask, singleLine = true, enabled = enabled)
        }

        val initial = render().primitives.toList()
        val initialPolicy = host.root.children[0].measurePolicy
        val initialModifier = host.root.children[0].modifier
        render()
        assertSame(initialPolicy, host.root.children[0].measurePolicy)
        assertSame(initialModifier, host.root.children[0].modifier)

        state = TextFieldState("world")
        assertNotEquals(initial, render().primitives.toList())

        style = TextStyle(size = 16.sp, color = Color(1f, 0f, 0f, 1f))
        val afterStyle = render().primitives.toList()
        mask = PasswordMask
        val masked = render()
        assertNotEquals(afterStyle, masked.primitives.toList())
        assertEquals(true, byTag(masked.semantics, "input").config[SemanticsProperties.Password])

        enabled = false
        assertEquals(true, byTag(render().semantics, "input").config[SemanticsProperties.Disabled])
    }
}
