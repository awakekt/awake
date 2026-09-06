/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.foundation.text.TextMeasurePolicy
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private fun measureText(
    constraints: Constraints = Constraints.of(0, 4000, 0, 4000),
    content: context(com.awakekt.awake.compose.runtime.Composer) () -> Unit,
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

        // Not exactly double: each density pass quantizes its complete intrinsic line independently.
        // Rounding can differ by one pixel in either direction.
        val doubled = root.children[0].width * 2
        assertTrue(
            root.children[1].width in (doubled - 1)..(doubled + 1),
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

        assertEquals(root.children[0].height, root.children[1].height, "the inherited 40sp survived")
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
    fun fontWeightSelectsARealAtlasFace() {
        val normal = glyphsOf { Text("A", style = TextStyle(size = 20.sp, weight = FontWeight.Normal)) }
        val medium = glyphsOf { Text("A", style = TextStyle(size = 20.sp, weight = FontWeight.Medium)) }

        assertNotEquals(normal.single().v0, medium.single().v0, "font weight still samples the regular atlas")
    }

    @Test
    fun aLongRunWrapsAtASpace() {
        // The same word twice. A glyph's x carries its own left bearing, so wrapping "aaaa wwww"
        // and comparing 'a' against 'w' measures the font rather than the wrap -- which is exactly
        // what the first version of this did, failing by 0.44px.
        val glyphs = glyphsOf {
            Text(
                "aaaa aaaa",
                Modifier.width(40.dp),
                style = TextStyle(lineHeight = 20f.sp),
            )
        }

        val secondRow = glyphs.filter { it.y > glyphs[0].y + 10f }
        assertTrue(secondRow.isNotEmpty(), "the run never wrapped")
        assertEquals(4, secondRow.size, "the wrap did not fall on the space")
        assertEquals(glyphs[0].x, secondRow[0].x, 0.01f, "the wrapped line did not return to x=0")
    }

    @Test
    fun aHardBreakStartsANewLine() {
        // The same two characters on both lines. A glyph's y carries its own bearing, so comparing
        // `a` against `c` measures the font, not the line break -- which is what the first version
        // of this test did, and it failed with four distinct rows for four glyphs.
        val glyphs = glyphsOf { Text("ab\nab", style = TextStyle(lineHeight = 20f.sp)) }

        assertEquals(4, glyphs.size, "the newline itself must not become a glyph")
        assertTrue(glyphs[2].y > glyphs[0].y, "the second line did not move down")
        assertEquals(20f, glyphs[2].y - glyphs[0].y, 0.01f, "the drop was not one line box")
        // The other half of a break, which a y-only assertion cannot see.
        assertEquals(glyphs[0].x, glyphs[2].x, 0.01f, "the second line did not return to x=0")
    }

    @Test
    fun compactMultilineLineHeightDoesNotOverlapGlyphs() {
        val glyphs = glyphsOf { Text("Ag\nAg", style = TextStyle(size = 18.sp, lineHeight = 18f.sp)) }
        val firstLineBottom = glyphs.take(2).maxOf { it.y + it.h }
        val secondLineTop = glyphs.drop(2).minOf { it.y }

        assertTrue(secondLineTop >= firstLineBottom, "multiline glyphs overlap: $glyphs")
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
