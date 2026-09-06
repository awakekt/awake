/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.interaction.Interaction
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.style.MutableStyleState
import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.foundation.style.StyleState
import com.awakekt.awake.compose.foundation.style.disabled
import com.awakekt.awake.compose.foundation.style.hovered
import com.awakekt.awake.compose.foundation.style.pressed
import com.awakekt.awake.compose.foundation.style.rememberStyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.graphics.RectangleShape
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.PathCommand
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.bounds
import com.awakekt.awake.core.graphics2d.tessellateStroke
import com.awakekt.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val base = Color(0.2f, 0.2f, 0.2f, 1f)
private val hover = Color(0.4f, 0.4f, 0.4f, 1f)
private val press = Color(0.6f, 0.6f, 0.6f, 1f)

/** A button-shaped style: a base, brighter on hover, brighter still while held, faded when off. */
private val buttonStyle = Style {
    background(base)
    cornerRadius(4.dp)
    contentPadding(8.dp)
    hovered { background(hover) }
    pressed { background(press) }
    disabled { alpha(0.5f) }
}

private fun styled(state: StyleState, modifier: Modifier = Modifier.size(40.dp)): List<UiDrawPrimitive> {
    val root = LayoutNode(ColumnMeasurePolicy())
    composeInto(root) { Spacer(modifier.styleable(state, buttonStyle)) }
    root.layoutTree(Constraints.of(0, 200, 0, 200))
    return Painter().paint(root)
}

private fun quadColor(primitives: List<UiDrawPrimitive>): Color =
    primitives.filterIsInstance<UiDrawPrimitive.RoundedQuad>().first().color

/**
 * A style is a function of state, resolved before the chain is built.
 *
 * `ui-core` could not do this: `hasResolvedVisuals()` had to resolve a style before claiming a slot,
 * so it assumed not-hovered and rechecked later -- and that guess is why `smartColumn` needed a
 * three-strategy dispatch at all.
 */
class StyleResolutionTest {

    @Test
    fun theBaseStyleAppliesWhenNothingIsHappening() {
        assertEquals(base, quadColor(styled(StyleState.Default)))
    }

    @Test
    fun hoverBranchesWithoutTheCallerAskingTwice() {
        val state = MutableStyleState(isHovered = true)

        assertEquals(hover, quadColor(styled(state)))
    }

    @Test
    fun pressWinsOverHoverBecauseItIsDeclaredLater() {
        // Declaration order is the precedence rule, which is what makes a style readable top to
        // bottom instead of needing a priority table.
        val state = MutableStyleState(isHovered = true, isPressed = true)

        assertEquals(press, quadColor(styled(state)))
    }

    @Test
    fun disabledReadsInverted() {
        val state = MutableStyleState(isEnabled = false)

        val alpha = quadColor(styled(state)).a
        assertEquals(0.5f, alpha, "the disabled rule never fired")
    }

    @Test
    fun aStateWithNoRuleChangesNothing() {
        val state = MutableStyleState(isSelected = true, isChecked = true)

        assertEquals(base, quadColor(styled(state)))
    }
}

/** The box model, which is not cosmetic: swapping the two paddings paints the wrong picture. */
class StyleBoxModelTest {
    @Test
    fun partialBorderOmitsTheSharedEndEdgeWithoutChangingTheOuterEdges() {
        val border = Color.White
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Spacer(
                Modifier.size(40.dp).border(
                    width = 1.dp,
                    color = border,
                    sides = BorderSides(top = true, end = false, bottom = true, start = true),
                ),
            )
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        // One mesh reaches the frame; which edges are in it is asserted against the builder below,
        // because a border is tessellated now and triangles cannot say "this edge was omitted".
        assertEquals(1, Painter().paint(root).filterIsInstance<UiDrawPrimitive.Mesh>().size)

        // The arguments BorderNode passes for a 1dp border on a 40x40 node at density 1.
        val path = partialBorderPath(
            bounds = Rectangle(0.5f, 0.5f, 39f, 39f),
            shape = RectangleShape,
            sides = BorderSides(top = true, end = false, bottom = true, start = true),
            density = 1f,
            inset = 0.5f,
        )
        val verticalEndEdge = path.commands.filterIsInstance<PathCommand.LineTo>().any { command ->
            command.x == 39.5f && command.y > 0.5f && command.y < 39.5f
        }

        assertTrue(!verticalEndEdge, "the omitted shared edge was still painted")
        assertTrue(
            path.commands.filterIsInstance<PathCommand.LineTo>().any { it.x == 0.5f && it.y == 0.5f },
            "the start edge disappeared with the end edge",
        )
    }

    @Test
    fun partialRoundedBorderKeepsOnlyTheJoinedOuterCornerArcs() {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Spacer(
                Modifier.size(40.dp).border(
                    width = 1.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                    sides = BorderSides(top = true, end = false, bottom = true, start = true),
                ),
            )
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        assertEquals(1, Painter().paint(root).filterIsInstance<UiDrawPrimitive.Mesh>().size)

        val path = partialBorderPath(
            bounds = Rectangle(0.5f, 0.5f, 39f, 39f),
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
            sides = BorderSides(top = true, end = false, bottom = true, start = true),
            density = 1f,
            inset = 0.5f,
        )
        assertEquals(2, path.commands.filterIsInstance<PathCommand.ArcTo>().size)
    }

    @Test
    fun styleShapeDrivesBothTheBackgroundAndBorder() {
        val styled = Style {
            background(base)
            border(1.dp, hover)
            shape(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
        }
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) { Spacer(Modifier.size(40.dp).styleable(StyleState.Default, styled)) }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        val primitives = Painter().paint(root)
        assertEquals(1, primitives.filterIsInstance<UiDrawPrimitive.FilledPath>().size)
        assertEquals(1, primitives.filterIsInstance<UiDrawPrimitive.Mesh>().size)
    }

    @Test
    fun borderedRoundedStyleDrawsOneContinuousRoundedRing() {
        val border = Color.White
        val style = Style {
            background(base)
            border(1.dp, border)
            cornerRadius(8.dp)
        }
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) { Spacer(Modifier.size(40.dp).styleable(StyleState.Default, style)) }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        val primitives = Painter().paint(root)

        // The fill is one full, undivided rounded rect -- no inner rect subtracted from it to
        // fake a ring; a real stroke needs no such workaround.
        val fill = primitives.filterIsInstance<UiDrawPrimitive.RoundedQuad>().single { it.color == base }
        assertEquals(40f, fill.w)
        assertEquals(40f, fill.h)
        assertEquals(8f, fill.radius)

        // The border is one continuous stroked outline, inset by half its own width so it
        // paints fully inside the box.
        val ring = primitives.filterIsInstance<UiDrawPrimitive.Mesh>().single().placedMesh()
        // Centreline (0.5, 0.5, 39, 39) grown by half the 1px stroke and then by the fringe,
        // min(AA_FRINGE_PX, stroke / 2) = 0.5.
        assertEquals(Rectangle(-0.5f, -0.5f, 41f, 41f), ring.bounds())

        // A real corner arc, not a square one: a naive right-angle outline never places a
        // vertex inside the 7x7 corner square a rounded corner has to cut through.
        val cornerCut = ring.vertices.any { it.position.x < 7f && it.position.y < 7f }
        assertTrue(cornerCut, "the stroked outline never curves away from the corner")
    }

    @Test
    fun contentPaddingInsetsEachAxisIndependently() {
        // Tailwind almost never pads uniformly -- `px-2 py-0.5`, `px-3 py-1.5`, `px-4 py-2`. Before
        // this existed a recipe had to pick one number, which is visible on anything short and wide.
        //
        // Measured on a wrapping box: content padding insets the *content* inside the background, so
        // a fixed-size node would show nothing -- the box stays its declared size and only the child
        // moves. That is why this asserts on the background a shrink-wrapping parent resolves to.
        val state = StyleState.Default
        val padded = Style {
            background(base)
            contentPadding(horizontal = 12.dp, vertical = 4.dp)
        }
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Box(Modifier.styleable(state, padded)) { Spacer(Modifier.size(20.dp)) }
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        val quad = Painter().paint(root).filterIsInstance<UiDrawPrimitive.Quad>().first()

        assertEquals(20f + 24f, quad.w, 0.5f, "horizontal padding did not inset by its own axis")
        assertEquals(20f + 8f, quad.h, 0.5f, "vertical padding took the horizontal value")
    }

    @Test
    fun theUniformOverloadStillSetsBothAxes() {
        val state = StyleState.Default
        val uniform = Style {
            background(base)
            contentPadding(10.dp)
        }
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Box(Modifier.styleable(state, uniform)) { Spacer(Modifier.size(30.dp)) }
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        val quad = Painter().paint(root).filterIsInstance<UiDrawPrimitive.Quad>().first()

        assertEquals(50f, quad.w, 0.5f)
        assertEquals(50f, quad.h, 0.5f)
    }

    @Test
    fun externalPaddingSitsOutsideTheBackground() {
        val state = StyleState.Default
        val spaced = Style {
            background(base)
            externalPadding(6.dp)
        }
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) { Spacer(Modifier.size(40.dp).styleable(state, spaced)) }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        val quad = Painter().paint(root).filterIsInstance<UiDrawPrimitive.Quad>().first()

        assertEquals(6f, quad.x, "the background covered the margin")
        assertEquals(28f, quad.w, "40 less 6 either side")
    }

    @Test
    fun contentPaddingSitsInsideTheBackground() {
        val state = StyleState.Default
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) { Spacer(Modifier.size(40.dp).styleable(state, buttonStyle)) }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        val quad = Painter().paint(root).filterIsInstance<UiDrawPrimitive.RoundedQuad>().first()

        assertEquals(0f, quad.x, "content padding pushed the background in")
        assertEquals(40f, quad.w)
    }

    @Test
    fun aBorderIsPaintedInsideTheBackgroundBox() {
        val bordered = Style {
            background(base)
            border(2.dp, hover)
        }
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) { Spacer(Modifier.size(40.dp).styleable(StyleState.Default, bordered)) }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        val primitives = Painter().paint(root)

        assertTrue(primitives.size >= 2, "the border was never painted")
    }
}

/** State fed from a real InteractionSource, which is where it comes from in a live frame. */
class StyleStateFromInteractionTest {

    @Test
    fun theThreePointerFlagsTrackTheSource() {
        val source = InteractionSource()
        val state = MutableStyleState()
        source.tryEmit(Interaction.Hover.Enter)
        source.tryEmit(Interaction.Press.Press)

        composeInto(LayoutNode(ColumnMeasurePolicy())) { state.updateFrom(source) }

        assertTrue(state.isHovered)
        assertTrue(state.isPressed)
    }

    @Test
    fun selectionAndCheckedAreLeftAlone() {
        // A checkbox is checked because its model says so, not because a pointer touched it.
        val source = InteractionSource()
        val state = MutableStyleState(isSelected = true, isChecked = true)

        composeInto(LayoutNode(ColumnMeasurePolicy())) { state.updateFrom(source) }

        assertTrue(state.isSelected)
        assertTrue(state.isChecked)
    }

    @Test
    fun aRememberedStateSurvivesTheNextPass() {
        val source = InteractionSource()
        val root = LayoutNode(ColumnMeasurePolicy())
        val seen = mutableListOf<StyleState>()
        val content: context(Composer)
        () -> Unit = {
            Column {
                seen += rememberStyleState(source)
                Spacer(Modifier.size(10.dp))
            }
        }
        composeInto(root, content)
        composeInto(root, content)

        assertTrue(seen[0] === seen[1], "a new state object every pass loses nothing but allocates")
    }
}
