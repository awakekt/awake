/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.blocksGameplayKeys
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val red = Color(1f, 0f, 0f, 1f)

private fun at(x: Int, y: Int, down: Boolean = false) =
    FrameInput(viewportWidth = 100, viewportHeight = 100, pointerX = x, pointerY = y, pointerDown = down)

/**
 * The frame loop: input against last frame's tree, then reconcile, measure, place, paint.
 *
 * Order is the whole contract and every way of getting it wrong is silent -- paint before place and
 * every primitive lands at the previous frame's position, with no error anywhere.
 */
class ComposeHostTest {

    @Test
    fun theContentLambdaRunsExactlyOncePerFrame() {
        // The claim the engine exists for. `ui-core` re-executes a container's content as a trial
        // pass, 7,696 times per frame on the Checkout Form.
        val host = ComposeHost()
        var runs = 0
        val content: context(Composer)
        () -> Unit = {
            runs++
            Column { Spacer(Modifier.size(10.dp).background(red)) }
        }

        host.frame(at(0, 0), content)
        assertEquals(1, runs)

        host.frame(at(0, 0), content)
        assertEquals(2, runs, "one run per frame, not one per measurement")
    }

    @Test
    fun theRootFillsTheViewport() {
        val host = ComposeHost()

        host.frame(FrameInput(viewportWidth = 80, viewportHeight = 40)) { Spacer(Modifier.size(5.dp)) }

        assertEquals(80, host.root.width)
        assertEquals(40, host.root.height)
    }

    @Test
    fun aResizeRemeasures() {
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = { Spacer(Modifier.size(5.dp)) }

        host.frame(FrameInput(viewportWidth = 80, viewportHeight = 40), content)
        host.frame(FrameInput(viewportWidth = 120, viewportHeight = 90), content)

        assertEquals(120, host.root.width)
        assertEquals(90, host.root.height)
    }

    @Test
    fun aFramePaintsWhatWasDeclared() {
        val host = ComposeHost()

        val output = host.frame(at(0, 0)) { Spacer(Modifier.size(10.dp).background(red)) }

        val quad = output.primitives.filterIsInstance<UiDrawPrimitive.Quad>().single()
        assertEquals(listOf(0f, 0f, 10f, 10f), listOf(quad.x, quad.y, quad.w, quad.h))
    }

    @Test
    fun aFrameReportsSemantics() {
        val host = ComposeHost()

        val output = host.frame(at(0, 0)) { Spacer(Modifier.size(10.dp).testTag("probe")) }

        assertEquals(1, output.semantics.size)
    }
}

/**
 * Level in, edges out.
 *
 * A host reports "the button is down right now", the shape every windowing toolkit gives. Turning
 * that into press/release exactly once is the host's job, and doing it wrong means a click fires on
 * every frame the finger is held.
 */
class PointerEdgeTest {

    private class Counter {
        var clicks = 0
    }

    private fun hostWithButton(): Pair<ComposeHost, Counter> {
        val host = ComposeHost()
        val counter = Counter()
        val content: context(Composer)
        () -> Unit = {
            Spacer(Modifier.size(50.dp).clickable { counter.clicks++ })
        }
        // First frame lays the tree out; nothing is placed before it, so input has no geometry.
        host.frame(at(5, 5), content)
        return host to counter
    }

    @Test
    fun aHeldPressIsOnePressNotOnePerFrame() {
        val (host, counter) = hostWithButton()
        val content: context(Composer)
        () -> Unit = {
            Spacer(Modifier.size(50.dp).clickable { counter.clicks++ })
        }

        repeat(10) { host.frame(at(5, 5, down = true), content) }
        host.frame(at(5, 5, down = false), content)

        assertEquals(1, counter.clicks, "the click fired once per held frame")
    }

    @Test
    fun theFirstFrameDispatchesNothing() {
        // Nothing has been placed yet, so a hit test would run against a zero-sized tree and a
        // press on frame one would silently miss -- or worse, hit the wrong thing.
        val host = ComposeHost()
        val counter = Counter()

        host.frame(at(5, 5, down = true)) {
            Spacer(Modifier.size(50.dp).clickable { counter.clicks++ })
        }

        assertEquals(0, counter.clicks)
    }

    @Test
    fun inputIsResolvedAgainstLastFramesTree() {
        // The one-frame-lag class: the press is dispatched before this frame's reconcile, against
        // geometry that already exists rather than geometry being computed.
        val host = ComposeHost()
        val counter = Counter()
        val content: context(Composer)
        () -> Unit = {
            Spacer(Modifier.size(50.dp).clickable { counter.clicks++ })
        }
        host.frame(at(5, 5), content)

        host.frame(at(5, 5, down = true), content)
        host.frame(at(5, 5, down = false), content)

        assertEquals(1, counter.clicks)
    }

    @Test
    fun aRetainedClickTargetSurvivesPressAndReleaseFrames() {
        val host = ComposeHost()
        val counter = Counter()
        val content: context(Composer)
        () -> Unit = {
            Spacer(
                Modifier.size(50.dp)
                    .background(red)
                    .testTag("retained-click-target")
                    .clickable { counter.clicks++ },
            )
        }

        val idle = host.frame(at(5, 5), content)
        val pressed = host.frame(at(5, 5, down = true), content)
        host.frame(at(5, 5, down = false), content)

        // The node is reconciled, not rebuilt, so the press and the release land on one target.
        assertEquals(1, counter.clicks, "the retained click target lost its release")
        assertEquals(idle.primitives, pressed.primitives, "a press changed the rendered output")
        assertEquals(
            idle.semantics.map { listOf(it.testTag, it.x, it.y, it.width, it.height) },
            pressed.semantics.map { listOf(it.testTag, it.x, it.y, it.width, it.height) },
            "a press changed the accessibility output",
        )
    }

    @Test
    fun thePointerLeavingTheWindowEndsAHeldPress() {
        // Otherwise the node that captured the pointer holds it forever and every later frame is
        // delivered to something the user is no longer touching.
        val (host, counter) = hostWithButton()
        val content: context(Composer)
        () -> Unit = {
            Spacer(Modifier.size(50.dp).clickable { counter.clicks++ })
        }
        host.frame(at(5, 5, down = true), content)
        assertTrue(host.frame(at(5, 5, down = true), content).ownership.isCaptured)

        val output = host.frame(FrameInput(viewportWidth = 100, viewportHeight = 100), content)

        assertFalse(output.ownership.isCaptured, "the pointer left and the capture survived")
    }

    @Test
    fun captureIsClaimedWhilePressedAndReleasedAfter() {
        val (host, counter) = hostWithButton()
        val content: context(Composer)
        () -> Unit = {
            Spacer(Modifier.size(50.dp).clickable { counter.clicks++ })
        }

        val held = host.frame(at(5, 5, down = true), content)
        val let = host.frame(at(5, 5, down = false), content)

        assertTrue(held.ownership.isCaptured)
        assertTrue(held.ownership.blocksGameplayKeys, "gameplay kept reading the mouse mid-click")
        assertFalse(let.ownership.isCaptured)
    }

    @Test
    fun pressingEmptySpaceClaimsNothing() {
        // A click that hits no interactive node must not block gameplay -- that is the whole reason
        // ownership is reported rather than assumed.
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = { Spacer(Modifier.size(10.dp)) }
        host.frame(at(90, 90), content)

        val output = host.frame(at(90, 90, down = true), content)

        assertFalse(output.ownership.isCaptured)
        assertFalse(output.ownership.blocksGameplayKeys)
    }
}

/**
 * A gesture outlives the modifier instance that started it.
 *
 * The chain is rebuilt on every pass, so the `clickable` that saw the press is a different object
 * from the one that sees the release one frame later. A link that remembered its own press would
 * forget it, and a click that spans two frames -- which every real click does -- would never fire.
 * The dispatcher holds that memory by node instead.
 */
class GestureAcrossFramesTest {

    @Test
    fun aLongPressSurvivesTheChainBeingRebuiltWhileThePointerIsStill() {
        val host = ComposeHost()
        var clicks = 0
        var longClicks = 0
        val content: context(Composer)
        () -> Unit = {
            Spacer(Modifier.size(50.dp).combinedClickable({ clicks++ }, { longClicks++ }))
        }

        host.frame(at(5, 5), content)
        host.frame(FrameInput(100, 100, 5, 5, pointerDown = true, deltaSeconds = 0.1f), content)
        host.frame(FrameInput(100, 100, 5, 5, pointerDown = true, deltaSeconds = 0.4f), content)
        host.frame(at(5, 5, down = false), content)

        assertEquals(0, clicks)
        assertEquals(1, longClicks)
    }

    @Test
    fun aClickSurvivesTheChainBeingRebuiltBetweenPressAndRelease() {
        val host = ComposeHost()
        var clicks = 0
        var links = 0
        val content: context(Composer)
        () -> Unit = {
            links++
            Spacer(Modifier.size(50.dp).clickable { clicks++ })
        }

        host.frame(at(5, 5), content)
        host.frame(at(5, 5, down = true), content)
        host.frame(at(5, 5, down = false), content)

        assertEquals(1, clicks)
        assertEquals(3, links, "the chain was not rebuilt, so this proves nothing")
    }

    @Test
    fun hoverSurvivesItToo() {
        val host = ComposeHost()
        val source = InteractionSource()
        val content: context(Composer)
        () -> Unit = {
            Spacer(Modifier.size(50.dp).hoverable(source))
        }

        host.frame(at(80, 80), content)
        host.frame(at(5, 5), content)
        host.frame(at(6, 6), content)

        assertTrue(source.isHovered, "hover was lost when the chain rebuilt")

        host.frame(at(90, 90), content)
        assertFalse(source.isHovered)
    }

    @Test
    fun aDragOffTheButtonCancelsAcrossFrames() {
        val host = ComposeHost()
        var clicks = 0
        val source = InteractionSource()
        val content: context(Composer)
        () -> Unit = {
            Spacer(Modifier.size(50.dp).clickable(source) { clicks++ })
        }
        host.frame(at(5, 5), content)
        host.frame(at(5, 5, down = true), content)

        host.frame(at(90, 90, down = true), content)
        host.frame(at(90, 90, down = false), content)

        assertEquals(0, clicks, "leaving mid-press still fired the click")
        assertFalse(source.isPressed, "the button stayed looking held down")
    }

    @Test
    fun draggingBackOntoTheButtonReArmsIt() {
        // How every button on every platform behaves: the press is suspended, not lost.
        val host = ComposeHost()
        var clicks = 0
        val source = InteractionSource()
        val content: context(Composer)
        () -> Unit = {
            Spacer(Modifier.size(50.dp).clickable(source) { clicks++ })
        }
        host.frame(at(5, 5), content)
        host.frame(at(5, 5, down = true), content)

        host.frame(at(90, 90, down = true), content)
        assertFalse(source.isPressed, "still looked held down after leaving")
        host.frame(at(6, 6, down = true), content)
        assertTrue(source.isPressed, "did not re-arm on the way back")
        host.frame(at(6, 6, down = false), content)

        assertEquals(1, clicks)
    }

    @Test
    fun modalLayerSetsIsModalOpenOnOutputEvenWithoutPointerEvents() {
        val host = ComposeHost()
        val outputWithoutModal = host.frame(at(0, 0)) {
            Spacer(Modifier.size(50.dp))
        }
        assertFalse(outputWithoutModal.ownership.isModalOpen)

        val outputWithModal = host.frame(at(0, 0)) {
            com.awakekt.awake.compose.ui.layout.Layer(
                kind = com.awakekt.awake.compose.ui.layout.LayerKind.Dialog,
                modal = true,
                measurePolicy = com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy(),
            ) {
                Spacer(Modifier.size(50.dp))
            }
        }
        assertTrue(outputWithModal.ownership.isModalOpen, "opening a modal layer must immediately set isModalOpen")
    }
}
