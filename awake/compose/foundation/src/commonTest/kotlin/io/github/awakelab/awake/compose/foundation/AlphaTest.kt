/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.draw.alpha
import io.github.awakelab.awake.compose.ui.draw.drawBehind
import io.github.awakelab.awake.compose.ui.graphics.Painter
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.layout.layoutTree
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.core.graphics2d.UiLinearGradient
import io.github.awakelab.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private val red = Color(1f, 0f, 0f, 1f)

private fun alphasOf(content: context(Composer) () -> Unit): List<Float> {
    val root = LayoutNode(ColumnMeasurePolicy())
    composeInto(root, content)
    root.layoutTree(Constraints.of(0, 200, 0, 200))
    return Painter().paint(root).filterIsInstance<UiDrawPrimitive.Quad>().map { it.color.a }
}

class AlphaTest {

    @Test
    fun aDimAppliesToWhatComesAfterItInTheChain() {
        val alphas = alphasOf { Spacer(Modifier.size(20.dp).alpha(0.5f).background(red)) }

        assertEquals(listOf(0.5f), alphas)
    }

    @Test
    fun aDimDoesNotReachBackwards() {
        // Chain position is the whole API here: a background declared outside the dim stays opaque.
        val alphas = alphasOf { Spacer(Modifier.size(20.dp).background(red).alpha(0.5f)) }

        assertEquals(listOf(1f), alphas)
    }

    @Test
    fun nestedDimsCompose() {
        // `ui-core`'s transform stack documented that nested scale blocks do NOT compose. On a
        // retained tree the factor composes down, so this is 0.5 * 0.5 rather than the inner one
        // winning.
        val alphas = alphasOf {
            Spacer(Modifier.size(20.dp).alpha(0.5f).alpha(0.5f).background(red))
        }

        assertEquals(listOf(0.25f), alphas)
    }

    @Test
    fun aDimReachesChildren() {
        val alphas = alphasOf {
            Column(Modifier.size(60.dp).alpha(0.5f)) {
                Spacer(Modifier.size(20.dp).background(red))
                Spacer(Modifier.size(20.dp).background(red))
            }
        }

        assertEquals(listOf(0.5f, 0.5f), alphas)
    }

    @Test
    fun theDimIsPoppedWhenTheSubtreeEnds() {
        // A dim that escaped its subtree would silently darken the rest of the frame.
        val alphas = alphasOf {
            Column(Modifier.size(60.dp)) {
                Spacer(Modifier.size(20.dp).alpha(0.5f).background(red))
                Spacer(Modifier.size(20.dp).background(red))
            }
        }

        assertEquals(listOf(0.5f, 1f), alphas)
    }

    @Test
    fun aFullyOpaqueAlphaIsNotAChainLink() {
        // Not merely a no-op: it must not cost a link, because every link is per-node per-frame work.
        val modifier = Modifier.size(20.dp)

        assertSame(modifier, modifier.alpha(1f))
    }
}

/**
 * Every colour an emitted primitive carries is dimmed, not just the two shapes `DrawScope` draws.
 *
 * An `emit()` that came out at full brightness under a dim is the silent kind of wrong: the caller
 * sees their gizmo painted, just not faded, and nothing says why.
 */
class AlphaEmitTest {

    private fun emitted(primitive: UiDrawPrimitive): UiDrawPrimitive {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Spacer(Modifier.size(20.dp).alpha(0.5f).drawBehind { emit(primitive) })
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        return Painter().paint(root).single()
    }

    @Test
    fun aGlyphIsDimmed() {
        val out = emitted(
            UiDrawPrimitive.Glyph(0f, 0f, 8f, 8f, 0f, 0f, 1f, 1f, red, "a"),
        ) as UiDrawPrimitive.Glyph

        assertEquals(0.5f, out.color.a)
    }

    @Test
    fun aShadowIsDimmed() {
        val out = emitted(
            UiDrawPrimitive.ShadowQuad(0f, 0f, 8f, 8f, 0f, 0f, 0f, 0f, 0f, red),
        ) as UiDrawPrimitive.ShadowQuad

        assertEquals(0.5f, out.color.a)
    }

    @Test
    fun everyGradientStopIsDimmed() {
        // Dimming one stop and not the rest would flatten the ramp rather than fade it.
        val gradient = UiLinearGradient(red, red, red, red)
        val out = emitted(
            UiDrawPrimitive.GradientQuad(0f, 0f, 8f, 8f, gradient),
        ) as UiDrawPrimitive.GradientQuad

        assertEquals(
            listOf(0.5f, 0.5f, 0.5f, 0.5f),
            listOf(
                out.gradient.topLeft.a,
                out.gradient.topRight.a,
                out.gradient.bottomRight.a,
                out.gradient.bottomLeft.a,
            ),
        )
    }

    @Test
    fun aClipPassesThroughUntouched() {
        // It carries no colour, so dimming has nothing to do and must not corrupt the rect.
        val rect = Rectangle(1f, 2f, 3f, 4f)
        val out = emitted(UiDrawPrimitive.ClipPush(rect)) as UiDrawPrimitive.ClipPush

        assertEquals(rect, out.rect)
    }
}
