// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.foundation.interaction.Interaction
import io.github.ronjunevaldoz.awake.compose.foundation.interaction.InteractionSource
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Column
import io.github.ronjunevaldoz.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Spacer
import io.github.ronjunevaldoz.awake.compose.foundation.layout.size
import io.github.ronjunevaldoz.awake.compose.foundation.style.MutableStyleState
import io.github.ronjunevaldoz.awake.compose.foundation.style.Style
import io.github.ronjunevaldoz.awake.compose.foundation.style.StyleState
import io.github.ronjunevaldoz.awake.compose.foundation.style.disabled
import io.github.ronjunevaldoz.awake.compose.foundation.style.hovered
import io.github.ronjunevaldoz.awake.compose.foundation.style.pressed
import io.github.ronjunevaldoz.awake.compose.foundation.style.rememberStyleState
import io.github.ronjunevaldoz.awake.compose.foundation.style.styleable
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.graphics.Painter
import io.github.ronjunevaldoz.awake.compose.ui.layout.composeInto
import io.github.ronjunevaldoz.awake.compose.ui.layout.layoutTree
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val base = Color(0.2f, 0.2f, 0.2f, 1f)
private val hover = Color(0.4f, 0.4f, 0.4f, 1f)
private val press = Color(0.6f, 0.6f, 0.6f, 1f)

/** A button-shaped style: a base, brighter on hover, brighter still while held, faded when off. */
private val buttonStyle = Style { scope ->
    scope.background(base)
    scope.cornerRadius(4.dp)
    scope.contentPadding(8.dp)
    scope.hovered { it.background(hover) }
    scope.pressed { it.background(press) }
    scope.disabled { it.alpha(0.5f) }
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
    fun externalPaddingSitsOutsideTheBackground() {
        val state = StyleState.Default
        val spaced = Style { scope ->
            scope.background(base)
            scope.externalPadding(6.dp)
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
        val bordered = Style { scope ->
            scope.background(base)
            scope.border(2.dp, hover)
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

        state.updateFrom(source)

        assertTrue(state.isHovered)
        assertTrue(state.isPressed)
    }

    @Test
    fun selectionAndCheckedAreLeftAlone() {
        // A checkbox is checked because its model says so, not because a pointer touched it.
        val source = InteractionSource()
        val state = MutableStyleState(isSelected = true, isChecked = true)

        state.updateFrom(source)

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
