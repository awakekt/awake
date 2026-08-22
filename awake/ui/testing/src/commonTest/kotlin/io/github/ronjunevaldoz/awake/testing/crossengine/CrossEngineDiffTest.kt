// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.testing.crossengine

import io.github.ronjunevaldoz.awake.compose.foundation.background
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Arrangement
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Column
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Row
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Spacer
import io.github.ronjunevaldoz.awake.compose.foundation.layout.size
import io.github.ronjunevaldoz.awake.compose.foundation.layout.fillMaxWidth
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.foundation.style.Style as ComposeStyle
import io.github.ronjunevaldoz.awake.compose.foundation.style.styleable
import io.github.ronjunevaldoz.awake.compose.foundation.style.hovered
import io.github.ronjunevaldoz.awake.compose.foundation.style.StyleState
import io.github.ronjunevaldoz.awake.compose.foundation.style.MutableStyleState
import io.github.ronjunevaldoz.awake.compose.foundation.text.Text
import io.github.ronjunevaldoz.awake.compose.ui.platform.ComposeHost
import io.github.ronjunevaldoz.awake.compose.ui.platform.FrameInput
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.core.math2d.px
import io.github.ronjunevaldoz.awake.core.math2d.sp
import io.github.ronjunevaldoz.awake.ui.UiInputState
import io.github.ronjunevaldoz.awake.ui.UiShape
import io.github.ronjunevaldoz.awake.ui.context.UiContext
import io.github.ronjunevaldoz.awake.ui.context.UiFrameInput
import io.github.ronjunevaldoz.awake.ui.layouts.ColumnScope
import io.github.ronjunevaldoz.awake.ui.layouts.column
import io.github.ronjunevaldoz.awake.ui.layouts.row
import io.github.ronjunevaldoz.awake.ui.layouts.surface
import io.github.ronjunevaldoz.awake.ui.modifier.height
import io.github.ronjunevaldoz.awake.ui.modifier.width
import io.github.ronjunevaldoz.awake.ui.modifier.fillMaxWidth as uiFillMaxWidth
import io.github.ronjunevaldoz.awake.ui.modifier.weight
import io.github.ronjunevaldoz.awake.ui.style.Style
import io.github.ronjunevaldoz.awake.ui.foundation.text.text
import io.github.ronjunevaldoz.awake.ui.theme.TextStyle
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.github.ronjunevaldoz.awake.ui.modifier.Modifier as UiModifierRoot

private const val VIEWPORT = 200

private val red = Color(1f, 0f, 0f, 1f)

/** Square and flat, so the comparison is about layout rather than about shape defaults. */
private val flatRed = Style {
    background(red)
    shape(UiShape.none)
    borderWidth(UiShape.none)
}

private fun composeFrame(content: context(Composer) () -> Unit): List<UiDrawPrimitive> =
    ComposeHost()
        .frame(FrameInput(viewportWidth = VIEWPORT, viewportHeight = VIEWPORT), content)
        .primitives

private fun uiCoreFrame(content: ColumnScope.(slot: Rectangle) -> Unit): List<UiDrawPrimitive> {
    val ui = UiContext()
    ui.beginFrame(
        UiFrameInput(
            viewportWidth = VIEWPORT.toFloat(),
            viewportHeight = VIEWPORT.toFloat(),
            input = UiInputState(pointerX = -100f, pointerY = -100f),
        ),
    )
    ui.createAbsolute(x = 0f, y = 0f).column(id = "root", content = content)
    return ui.finishFrame().primitives
}

private fun ColumnScope.box(id: String, size: Int) {
    surface(
        id = id,
        style = flatRed,
        modifier = UiModifierRoot.width(size.toFloat().px).height(size.toFloat().px),
    ) {}
}

/**
 * One scene, both engines, compared primitive for primitive.
 *
 * This is the check coverage cannot give. A line-coverage number says every line ran; this says the
 * replacement paints what the engine it replaces painted, for a whole scene, or names exactly which
 * field of which primitive moved.
 *
 * Every divergence has to be classified. An unexplained one fails, because "the new engine draws
 * something else" is the finding this tool exists to produce.
 */
class CrossEngineDiffTest {

    @Test
    fun aSingleBoxIsIdentical() {
        val reference = uiCoreFrame { box("only", 40) }
        val candidate = composeFrame { Spacer(Modifier.size(40.dp).background(red)) }

        assertEquals(emptyList(), PrimitiveDiff.compare(reference, candidate))
    }

    @Test
    fun stackedBoxesDivergeOnlyByTheDefaultGap() {
        // `ui-core` puts 8 dp between column children by default; Compose puts zero, and this engine
        // went back to Compose's. `mirror-map.md` records it and `README.md` calls the revert a
        // return *to* Compose rather than past it -- so it is expected here, and named.
        val reference = uiCoreFrame {
            box("a", 40)
            box("b", 20)
        }
        val candidate = composeFrame {
            Column {
                Spacer(Modifier.size(40.dp).background(red))
                Spacer(Modifier.size(20.dp).background(red))
            }
        }

        val divergences = PrimitiveDiff.compare(reference, candidate)
        val expected = listOf(
            ExpectedDivergence("y", "ui-core defaults to an 8 dp column gap; Compose and this engine default to zero"),
        )

        assertEquals(emptyList(), divergences.unexplained(expected), "an unexplained divergence")
        assertTrue(divergences.isNotEmpty(), "the gap divergence vanished -- update the expectation")
    }

    @Test
    fun matchingTheGapExplicitlyMakesTheScenesIdentical() {
        // The other half of the previous test: ask this engine for the same 8 dp and the output is
        // the same picture, which is what proves the divergence is a *default* and not a layout bug.
        val reference = uiCoreFrame {
            box("a", 40)
            box("b", 20)
        }
        val candidate = composeFrame {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.size(40.dp).background(red))
                Spacer(Modifier.size(20.dp).background(red))
            }
        }

        assertEquals(emptyList(), PrimitiveDiff.compare(reference, candidate))
    }

    @Test
    fun aNestedRowOfBoxesMatches() {
        val reference = uiCoreFrame {
            row(id = "row") {
                surface(id = "l", style = flatRed, modifier = UiModifierRoot.width(30f.px).height(20f.px)) {}
                surface(id = "r", style = flatRed, modifier = UiModifierRoot.width(50f.px).height(20f.px)) {}
            }
        }
        val candidate = composeFrame {
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(8.dp)) {
                Spacer(Modifier.size(30.dp, 20.dp).background(red))
                Spacer(Modifier.size(50.dp, 20.dp).background(red))
            }
        }

        assertEquals(emptyList(), PrimitiveDiff.compare(reference, candidate))
    }
}

/** The differ's own behaviour, so a green cross-engine run cannot be a vacuous one. */
class PrimitiveDiffTest {

    private fun quad(x: Float, y: Float) = UiDrawPrimitive.Quad(x, y, 1f, 1f, red)

    @Test
    fun identicalOutputHasNoDivergences() {
        assertEquals(emptyList(), PrimitiveDiff.compare(listOf(quad(0f, 0f)), listOf(quad(0f, 0f))))
    }

    @Test
    fun aMovedPrimitiveNamesTheFieldThatMoved() {
        val divergences = PrimitiveDiff.compare(listOf(quad(0f, 0f)), listOf(quad(0f, 5f)))

        assertEquals(1, divergences.size)
        assertEquals("y", divergences[0].field)
        assertEquals(0f, divergences[0].reference)
        assertEquals(5f, divergences[0].candidate)
    }

    @Test
    fun aLengthMismatchStopsAtTheCount() {
        // Reporting every later index as different would bury the one fact that matters.
        val divergences = PrimitiveDiff.compare(listOf(quad(0f, 0f)), emptyList())

        assertEquals(1, divergences.size)
        assertEquals("count", divergences[0].field)
    }

    @Test
    fun aDifferentPrimitiveKindIsReportedOnceNotFieldByField() {
        val rounded = UiDrawPrimitive.RoundedQuad(0f, 0f, 1f, 1f, red, 4f)
        val divergences = PrimitiveDiff.compare(listOf(quad(0f, 0f)), listOf(rounded))

        assertEquals(1, divergences.size)
        assertEquals("type", divergences[0].field)
    }

    @Test
    fun anUnknownPrimitiveKindSaysSoRatherThanComparingEqual() {
        // A primitive nobody taught the diff to read must not pass silently -- that is the
        // vacuous-green failure this repo keeps hitting.
        val texture = UiDrawPrimitive.Texture(0f, 0f, 1f, 1f, material = Any())
        val fields = PrimitiveDiff.fieldsOf(texture)

        assertEquals(PrimitiveDiff.UNCOMPARED, fields["fields"])
    }
}

/**
 * A styled surface -- the shape a real screen is made of -- through both engines.
 *
 * This is the scene the differ existed for. `ui-core` paints a surface from a `Style` with a
 * background, a corner radius and content padding; the compose engine resolves the same three from
 * its own `Style` and applies them as ordinary modifiers. If the box model differs anywhere, the
 * quads land in different places and this fails.
 */
class StyledSurfaceDiffTest {

    private val panel = Color(0.18f, 0.18f, 0.22f, 1f)

    @Test
    fun aFlatPanelIsIdentical() {
        val reference = uiCoreFrame {
            surface(
                id = "panel",
                style = Style {
                    background(panel)
                    shape(UiShape.none)
                    borderWidth(UiShape.none)
                },
                modifier = UiModifierRoot.width(60f.px).height(40f.px),
            ) {}
        }
        val candidate = composeFrame {
            Spacer(
                Modifier.size(60.dp, 40.dp).styleable(
                    StyleState.Default,
                    ComposeStyle { it.background(panel) },
                ),
            )
        }

        assertEquals(emptyList(), PrimitiveDiff.compare(reference, candidate))
    }

    @Test
    fun aRoundedPanelPaintsTheSameQuad() {
        // The rounded path is a different primitive kind, so this proves the shape decision agrees
        // and not merely the rectangle arithmetic.
        val reference = uiCoreFrame {
            surface(
                id = "panel",
                style = Style {
                    background(panel)
                    shape(6f.px)
                    borderWidth(UiShape.none)
                },
                modifier = UiModifierRoot.width(60f.px).height(40f.px),
            ) {}
        }
        val candidate = composeFrame {
            Spacer(
                Modifier.size(60.dp, 40.dp).styleable(
                    StyleState.Default,
                    ComposeStyle {
                        it.background(panel)
                        it.cornerRadius(6.dp)
                    },
                ),
            )
        }

        assertEquals(emptyList(), PrimitiveDiff.compare(reference.paintOnly(), candidate.paintOnly()))
    }

    @Test
    fun uiCoreAutoClipsARoundedSurfaceAndThisEngineDoesNot() {
        // Classified, not swept under the diff. `ui-core`'s `surface` always clips its content to
        // its own shape. Compose separates the two -- `background(color, shape)` paints and
        // `clip(shape)` clips -- so a caller who wants overflow visible can have it. This engine
        // follows Compose, which makes `ui-core` the outlier here rather than this engine.
        val reference = uiCoreFrame {
            surface(
                id = "panel",
                style = Style { background(panel); shape(6f.px); borderWidth(UiShape.none) },
                modifier = UiModifierRoot.width(60f.px).height(40f.px),
            ) {}
        }
        val candidate = composeFrame {
            Spacer(Modifier.size(60.dp, 40.dp).styleable(StyleState.Default, ComposeStyle {
                it.background(panel)
                it.cornerRadius(6.dp)
            }))
        }

        assertEquals(2, reference.size - reference.paintOnly().size, "ui-core stopped auto-clipping")
        assertEquals(0, candidate.size - candidate.paintOnly().size, "this engine started auto-clipping")
    }

    @Test
    fun aHoveredPanelPaintsItsHoverColour() {
        // `ui-core` cannot express this scene without a live pointer, because its style resolution
        // guesses not-hovered before a slot is claimed. Compared against the flat panel instead:
        // the same style must paint a different colour purely from state.
        val hovered = MutableStyleState(isHovered = true)
        val style = ComposeStyle { scope ->
            scope.background(panel)
            scope.hovered { it.background(red) }
        }
        val idle = composeFrame { Spacer(Modifier.size(20.dp).styleable(StyleState.Default, style)) }
        val active = composeFrame { Spacer(Modifier.size(20.dp).styleable(hovered, style)) }

        val divergences = PrimitiveDiff.compare(idle, active)
        assertEquals(1, divergences.size, "state changed more than the colour")
        assertEquals("color", divergences[0].field)
    }
}

/**
 * Glyphs, which are most of what a real screen paints.
 *
 * Every scene above is rectangles. A screen is text, and text is where the two engines have the most
 * arithmetic that could disagree -- advance accumulation, letter spacing, the em-to-pixel scale, and
 * the baseline the glyph quad hangs from.
 */
class TextDiffTest {

    private val ink = Color(0.9f, 0.9f, 0.92f, 1f)

    /** Both engines, one label, size pinned so the scene is about arithmetic and not about defaults. */
    private fun label(): List<Divergence> {
        val reference = uiCoreFrame {
            text("Total", color = ink, textStyle = TextStyle(size = 16f.sp), verticallyCentered = false)
        }
        val candidate = composeFrame {
            Text("Total", style = TextStyle(color = ink, size = 16f.sp))
        }
        return PrimitiveDiff.compare(reference.paintOnly(), candidate.paintOnly())
    }

    @Test
    fun everyGlyphAgreesHorizontallyAndInSize() {
        // Advance accumulation, letter spacing and the em-to-pixel scale all agree to the float.
        val moved = label().map { it.field }.toSet()

        assertEquals(setOf("y"), moved, "the glyph arithmetic diverged, not just the origin")
    }

    @Test
    fun uiCoreInkTopsAnUncenteredLineAndThisEngineKeepsTheLeading() {
        // Classified, not swept under the diff. `ui-core`'s uncentered branch places the pen at
        // `slot.y - blockMetrics.topPx`, which drags the topmost ink onto the slot's top edge and
        // throws the font's ascent leading away -- so a line of "Tom" and a line of "ton" start at
        // different heights. This engine hangs every glyph off its own metrics and leaves the
        // leading in, which is the same reason `ui-core`'s *centred* branch centres the cap box
        // rather than the ink box. Same lesson, applied to the branch that never got it.
        val divergences = label()
        val shifts = divergences.map { (it.candidate as Float) - (it.reference as Float) }.distinct()

        assertEquals(1, shifts.size, "not one constant shift -- the origin is not the only difference")
        assertTrue(shifts.single() > 0f, "this engine should sit lower, by the leading ui-core drops")
    }

    @Test
    fun theDefaultFontSizeDiverges() {
        // ui-core falls back to 16; this engine's LocalTextStyle carries 14, which is Compose's own
        // default body size. Named here so the size-pinned scene above cannot quietly absorb it.
        val reference = uiCoreFrame { text("Total", color = ink, verticallyCentered = false) }
        val candidate = composeFrame { Text("Total", style = TextStyle(color = ink)) }

        val divergences = PrimitiveDiff.compare(reference.paintOnly(), candidate.paintOnly())
        assertTrue(divergences.isNotEmpty(), "the default sizes agree now -- drop this test")
        assertTrue(divergences.all { it.field in setOf("x", "y", "w", "h") }, "more than geometry moved")
    }

    @Test
    fun theGlyphCountFollowsTheString() {
        // Guards the comparison above against being vacuously green on two empty lists -- a font that
        // resolved to no atlas would paint nothing on both sides and diff clean.
        val candidate = composeFrame { Text("Total", style = TextStyle(color = ink)) }

        assertEquals(5, candidate.count { it is UiDrawPrimitive.Glyph }, "no glyphs were painted")
    }
}

/**
 * A checkout line: a styled panel holding a label pushed apart from an amount by a weight.
 *
 * The first scene in this file that is a *screen* rather than a shape -- styling, nested layout,
 * weighted distribution and text all in one frame, which is where a divergence in any one of them
 * shows up as a divergence in all the others' positions.
 */
class CheckoutLineDiffTest {

    private val panel = Color(0.18f, 0.18f, 0.22f, 1f)
    private val ink = Color(0.9f, 0.9f, 0.92f, 1f)
    private val body = TextStyle(color = ink, size = 16f.sp)

    private fun diff(): List<Divergence> {
        val reference = uiCoreFrame {
            surface(
                id = "line",
                style = Style {
                    background(panel)
                    shape(UiShape.none)
                    borderWidth(UiShape.none)
                },
                modifier = UiModifierRoot.width(160f.px).height(24f.px),
            ) {
                row(id = "row", modifier = UiModifierRoot.uiFillMaxWidth()) {
                    text("Total", modifier = UiModifierRoot.weight(1f), textStyle = body, verticallyCentered = false)
                    text("42.00", textStyle = body, verticallyCentered = false)
                }
            }
        }
        val candidate = composeFrame {
            Column(
                Modifier.size(160.dp, 24.dp).styleable(StyleState.Default, ComposeStyle { it.background(panel) }),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Total", Modifier.weight(1f), style = body)
                    Text("42.00", style = body)
                }
            }
        }
        return PrimitiveDiff.compare(reference.paintOnly(), candidate.paintOnly())
    }

    @Test
    fun thePanelItselfIsIdentical() {
        // The styled box lands in the same place in both engines. Only its *content* moves, which is
        // what makes the divergences below an inset rather than a layout disagreement.
        assertEquals(emptyList(), diff().filter { it.index == 0 })
    }

    @Test
    fun onlyTheContentInsetMoves() {
        // Both classified, and both the same cause: `ui-core`'s `surfaceShapeDefaults` gives a
        // surface content padding, so its children start 8 px right and 8 px down. Compose's
        // `background` paints and insets nothing, and this engine followed Compose -- the same
        // revert-to-Compose the 8 dp column gap above records. The y allowance also absorbs the
        // text-leading shift `TextDiffTest` isolates.
        val expected = listOf(
            ExpectedDivergence("x", "ui-core's surface defaults to content padding; Compose and this engine default to none"),
            ExpectedDivergence("y", "the same default padding, plus the text leading TextDiffTest isolates"),
        )

        assertEquals(emptyList(), diff().unexplained(expected), "an unexplained divergence")
    }

    @Test
    fun theWeightStillPushedTheAmountToTheFarEdge() {
        // A per-field allowlist cannot tell a constant inset from a different weight distribution --
        // both only move x. This can: under an inset the two labels shift by *one* amount each, and
        // the trailing one shifts the opposite way as the row it is pushed against grows.
        // Rounded to a tenth: the shift is the same for every glyph, but subtracting two accumulated
        // advances leaves float noise in the last digit, and an exact `distinct()` counts that noise
        // as a second shift.
        val shifts = diff().filter { it.field == "x" }
            .groupBy { it.index <= 5 }
            .mapValues { (_, d) ->
                d.map { round(((it.candidate as Float) - (it.reference as Float)) * 10f) / 10f }.distinct()
            }

        shifts.forEach { (_, s) -> assertEquals(1, s.size, "one label's glyphs moved by differing amounts") }
        assertTrue(shifts.getValue(true).single() < 0f, "the leading label should shift left, by the dropped inset")
        assertTrue(shifts.getValue(false).single() > 0f, "the trailing label should shift right, toward the wider edge")
    }

    @Test
    fun theSceneIsNotEmpty() {
        // The panel plus ten glyphs. A scene that painted nothing would satisfy every assertion above.
        val candidate = composeFrame {
            Column(
                Modifier.size(160.dp, 24.dp).styleable(StyleState.Default, ComposeStyle { it.background(panel) }),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Total", Modifier.weight(1f), style = body)
                    Text("42.00", style = body)
                }
            }
        }

        assertEquals(10, candidate.count { it is UiDrawPrimitive.Glyph }, "not every glyph was painted")
        assertTrue(candidate.any { it is UiDrawPrimitive.Quad }, "the panel was never painted")
    }
}

/** Clip push/pop are structural, not paint. Dropping them compares the picture rather than the state stack. */
private fun List<UiDrawPrimitive>.paintOnly(): List<UiDrawPrimitive> = filter {
    it !is UiDrawPrimitive.ClipPush && it !is UiDrawPrimitive.ClipPop && it !is UiDrawPrimitive.ClipPathPush
}
