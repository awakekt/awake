// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.foundation.layout.BoxMeasurePolicy
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Column
import io.github.ronjunevaldoz.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.ronjunevaldoz.awake.compose.foundation.layout.padding
import io.github.ronjunevaldoz.awake.compose.foundation.text.Text
import io.github.ronjunevaldoz.awake.compose.foundation.text.TextMeasurePolicy
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.runtime.CompositionLocalProvider
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.graphics.Painter
import io.github.ronjunevaldoz.awake.compose.ui.layout.composeInto
import io.github.ronjunevaldoz.awake.compose.ui.layout.layoutTree
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.platform.LocalDensity
import io.github.ronjunevaldoz.awake.compose.ui.platform.LocalTextStyle
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import io.github.ronjunevaldoz.awake.compose.ui.unit.sp
import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive
import io.github.ronjunevaldoz.awake.ui.font.FontWeight
import io.github.ronjunevaldoz.awake.ui.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun measureText(
    constraints: Constraints = Constraints.of(0, 4000, 0, 4000),
    content: context(io.github.ronjunevaldoz.awake.compose.runtime.Composer) () -> Unit,
): LayoutNode {
    val root = LayoutNode(BoxMeasurePolicy())
    composeInto(root, content)
    root.measure(constraints)
    return root
}

class TextTest {

    @Test
    fun aLongerStringIsWider() {
        val root = measureText {
            Text("i")
            Text("iiiiiiiiii")
        }

        assertTrue(root.children[1].width > root.children[0].width)
    }

    @Test
    fun anEmptyStringHasNoWidthButKeepsLineHeight() {
        // A blank label still occupies a line -- collapsing it to zero height is what makes a row
        // of labels jump when one goes empty.
        val root = measureText { Text("") }

        assertEquals(0, root.children[0].width)
        assertTrue(root.children[0].height > 0)
    }

    @Test
    fun aLargerFontSizeMeasuresLarger() {
        val root = measureText {
            Text("hello", style = TextStyle(size = 10.sp))
            Text("hello", style = TextStyle(size = 30.sp))
        }

        assertTrue(root.children[1].width > root.children[0].width)
        assertTrue(root.children[1].height > root.children[0].height)
    }

    @Test
    fun densityScalesTextTheSameWayItScalesLayout() {
        val root = measureText {
            Text("hello", style = TextStyle(size = 10.sp))
            CompositionLocalProvider(LocalDensity, 2f) {
                Text("hello", style = TextStyle(size = 10.sp))
            }
        }

        // Not exactly double: text width is ceil'd so the last glyph is never clipped, and
        // ceil(2w) != 2*ceil(w). Within a pixel is the correct expectation.
        val doubled = root.children[0].width * 2
        assertTrue(
            root.children[1].width in (doubled - 1)..doubled,
            "expected about $doubled, was ${root.children[1].width}",
        )
    }

    @Test
    fun styleMergesOverTheInheritedOneRatherThanReplacingIt() {
        // Setting only a weight must not silently drop an ancestor's size.
        val root = measureText {
            CompositionLocalProvider(LocalTextStyle, TextStyle(size = 40.sp)) {
                Text("hello")
                Text("hello", style = TextStyle(weight = FontWeight.Bold))
            }
        }

        assertEquals(root.children[0].width, root.children[1].width, "the inherited 40sp survived")
    }

    @Test
    fun letterSpacingSitsBetweenGlyphsNotAfterTheLast() {
        // Trailing letter-spacing is the off-by-one that puts a phantom gap on the right edge of a
        // centred label.
        val plain = measureText { Text("ab", style = TextStyle(size = 20.sp)) }
        val spaced = measureText {
            Text("ab", style = TextStyle(size = 20.sp, letterSpacing = 10f.sp))
        }

        assertEquals(plain.children[0].width + 10, spaced.children[0].width, "one gap, not two")
    }

    @Test
    fun textIsMeasurableWithNoThemeProvided() {
        // LocalTextStyle's default carries a concrete size for exactly this reason.
        val root = measureText { Text("hello") }

        assertTrue(root.children[0].width > 0)
        assertTrue(root.children[0].height > 0)
    }

    @Test
    fun textIsMeasuredExactlyOnce() {
        val root = LayoutNode(BoxMeasurePolicy())
        composeInto(root) { Text("hello") }
        val node = root.children[0]
        val policy = node.measurePolicy as TextMeasurePolicy
        root.measure(Constraints.of(0, 4000, 0, 4000))

        assertEquals(policy, node.measurePolicy, "no re-entry replaced the policy mid-pass")
        assertTrue(node.width > 0)
    }
}

/**
 * Text paints, which it did not until the draw phase existed.
 *
 * The composable measured correctly and emitted nothing, so every layout test passed while the
 * screen stayed blank -- exactly the class a measure-only assertion cannot see.
 */
class TextPaintingTest {

    private fun glyphsOf(content: context(Composer) () -> Unit): List<UiDrawPrimitive.Glyph> {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root, content)
        root.layoutTree(Constraints.of(0, 400, 0, 400))
        return Painter().paint(root).filterIsInstance<UiDrawPrimitive.Glyph>()
    }

    @Test
    fun everyCharacterBecomesAGlyph() {
        val glyphs = glyphsOf { Text("abc") }

        assertEquals(3, glyphs.size)
    }

    @Test
    fun glyphsAdvanceLeftToRight() {
        val glyphs = glyphsOf { Text("abc") }

        assertTrue(glyphs[0].x < glyphs[1].x, "the second glyph did not advance")
        assertTrue(glyphs[1].x < glyphs[2].x)
    }

    @Test
    fun emptyTextPaintsNothing() {
        assertEquals(0, glyphsOf { Text("") }.size)
    }

    @Test
    fun glyphsCarryAtlasCoordinates() {
        // A glyph with no UVs samples the whole atlas and renders as a solid block.
        val glyph = glyphsOf { Text("a") }.single()

        assertTrue(glyph.u1 > glyph.u0, "degenerate horizontal UV span")
        assertTrue(glyph.v1 > glyph.v0, "degenerate vertical UV span")
    }

    @Test
    fun textIsPaintedWhereItIsPlaced() {
        val glyphs = glyphsOf {
            Column(Modifier.padding(12.dp)) { Text("a") }
        }

        assertTrue(glyphs.single().x >= 12f, "the padding was ignored when painting")
    }
}
