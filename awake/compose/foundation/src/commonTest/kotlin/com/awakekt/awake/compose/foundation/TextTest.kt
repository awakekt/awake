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
import com.awakekt.awake.compose.foundation.text.TextOverflow
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
import com.awakekt.awake.core.text.font.GlyphRect
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFontSamplingMode
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
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
    fun aStyleFontChangesTheMeasuredWidthFromTheAmbientOne() {
        // Same text, same size, same ambient LocalFont -- only the style's font differs, so a
        // width difference can only come from TextMeasurePolicy reading it.
        val root = measureText {
            Text("Hg", style = TextStyle(size = 24.sp))
            Text("Hg", style = TextStyle(size = 24.sp, font = UiFonts.bitmap()))
        }

        assertNotEquals(root.children[0].width, root.children[1].width, "a style font did not change the measured width")
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

    @Test
    fun textWithMaxLinesOneAndEllipsisCutsWithEllipsis() {
        val root = LayoutNode(BoxMeasurePolicy())
        composeInto(root) {
            Text(
                "The quick brown fox jumps over the lazy dog",
                Modifier.width(60.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
        root.layoutTree(Constraints.of(0, 400, 0, 400))
        val policy = root.children[0].measurePolicy as TextMeasurePolicy
        val lines = policy.runFor(1f, 1f).lines
        assertEquals(1, lines.size)
        assertTrue(lines[0].endsWith("\u2026"), "line must end with ellipsis: ${lines[0]}")
    }

    @Test
    fun textWithMaxLinesTwoAndEllipsisCutsSecondLineWithEllipsis() {
        val root = LayoutNode(BoxMeasurePolicy())
        composeInto(root) {
            Text(
                "The quick brown fox jumps over the lazy dog and runs across the field",
                Modifier.width(60.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
            )
        }
        root.layoutTree(Constraints.of(0, 400, 0, 400))
        val policy = root.children[0].measurePolicy as TextMeasurePolicy
        val lines = policy.runFor(1f, 1f).lines
        assertEquals(2, lines.size)
        assertTrue(lines[1].endsWith("\u2026"), "second line must end with ellipsis: ${lines[1]}")
    }

    @Test
    fun textWithSoftWrapFalseAndEllipsisCutsUnwrappedLine() {
        val root = LayoutNode(BoxMeasurePolicy())
        composeInto(root) {
            Text(
                "The quick brown fox jumps over the lazy dog",
                Modifier.width(50.dp),
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        root.layoutTree(Constraints.of(0, 400, 0, 400))
        val policy = root.children[0].measurePolicy as TextMeasurePolicy
        val lines = policy.runFor(1f, 1f).lines
        assertEquals(1, lines.size)
        assertTrue(lines[0].endsWith("\u2026"), "line must end with ellipsis: ${lines[0]}")
    }

    @Test
    fun textEllipsisNeverSplitsGraphemeCluster() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        val root = LayoutNode(BoxMeasurePolicy())
        composeInto(root) {
            Text(
                "Hi $family and friends",
                Modifier.width(35.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
        root.layoutTree(Constraints.of(0, 400, 0, 400))
        val policy = root.children[0].measurePolicy as TextMeasurePolicy
        val lines = policy.runFor(1f, 1f).lines
        assertEquals(1, lines.size)
        val line = lines[0]
        assertTrue(line.endsWith("\u2026"))
        val content = line.removeSuffix("\u2026")
        assertTrue(
            content == "Hi" || content == "Hi $family",
            "cluster was split into partial code points: $content",
        )
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
    fun eachTextNodeDrawsWithItsOwnStyleFont() {
        // Two siblings in the same frame, one with a style font and one inheriting the ambient
        // default. Each glyph must carry the instance it was actually measured and drawn with --
        // the renderer keys its atlas cache on that identity, so the wrong one here is a wrong
        // texture, not just a wrong metric.
        val bitmapFont = UiFonts.bitmap()
        val glyphs = glyphsOf {
            Text("a", style = TextStyle(font = bitmapFont))
            Text("a")
        }

        assertSame(bitmapFont, glyphs[0].font, "the styled run did not draw with its own font")
        assertSame(UiFonts.default(), glyphs[1].font, "the unstyled run did not fall back to LocalFont")
    }

    @Test
    fun textIsPaintedWhereItIsPlaced() {
        val glyphs = glyphsOf {
            Column(Modifier.padding(12.dp)) { Text("a") }
        }

        assertTrue(glyphs.single().x >= 12f, "the padding was ignored when painting")
    }

    @Test
    fun mixedScriptLineBatchesGlyphsByAtlasPage() {
        val face1 = StubUiFont(atlasHeight = 60, charCodes = setOf('A'.code, 'C'.code, 'E'.code))
        val face2 = StubUiFont(atlasHeight = 50, charCodes = setOf('B'.code, 'D'.code, 'F'.code))
        val fallback = UiFonts.fallback(face1, listOf(face2), maxAtlasHeight = 100)

        assertEquals(2, fallback.atlasPages.size)
        val page0 = fallback.atlasPages[0]
        val page1 = fallback.atlasPages[1]

        val glyphs = glyphsOf {
            Text("ABCDEF", style = TextStyle(font = fallback))
        }

        assertEquals(6, glyphs.size)
        // Glyphs for Page 0 are emitted consecutively, then glyphs for Page 1
        assertSame(page0, glyphs[0].font)
        assertSame(page0, glyphs[1].font)
        assertSame(page0, glyphs[2].font)
        assertSame(page1, glyphs[3].font)
        assertSame(page1, glyphs[4].font)
        assertSame(page1, glyphs[5].font)

        assertEquals(0, glyphs[0].page)
        assertEquals(0, glyphs[1].page)
        assertEquals(0, glyphs[2].page)
        assertEquals(1, glyphs[3].page)
        assertEquals(1, glyphs[4].page)
        assertEquals(1, glyphs[5].page)
    }
}

private class StubUiFont(
    override val atlasWidth: Int = 100,
    override val atlasHeight: Int = 50,
    private val charCodes: Set<Int> = emptySet(),
    override val samplingMode: UiFontSamplingMode = UiFontSamplingMode.DistanceField,
) : UiFont {
    override val cellSize: Int = 12
    override val textScaleStep: Float = 1f
    override val atlasPixelsRgba: ByteArray = ByteArray(atlasWidth * atlasHeight * 4)

    override fun uvFor(char: Char): GlyphRect? =
        if (char.code in charCodes) GlyphRect(0f, 0f, 1f, 1f) else null

    override fun hasGlyph(codePoint: Int): Boolean = codePoint in charCodes
    override fun hasGlyph(char: Char): Boolean = char.code in charCodes
    override fun glyphFor(char: Char, weight: FontWeight): GlyphRect? = uvFor(char)
    override fun glyphFor(codePoint: Int, weight: FontWeight): GlyphRect? =
        if (codePoint in charCodes) GlyphRect(0f, 0f, 1f, 1f) else null
}
