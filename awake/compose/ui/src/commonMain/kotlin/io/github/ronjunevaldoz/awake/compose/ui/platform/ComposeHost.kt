// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.platform

import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.runtime.CompositionLocalProvider
import io.github.ronjunevaldoz.awake.compose.runtime.provides
import io.github.ronjunevaldoz.awake.compose.ui.focus.FocusOwner
import io.github.ronjunevaldoz.awake.compose.ui.graphics.Painter
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEvent
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerEventType
import io.github.ronjunevaldoz.awake.compose.ui.input.pointer.PointerInputDispatcher
import io.github.ronjunevaldoz.awake.compose.ui.layout.Measurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasurePolicy
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureResult
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureScope
import io.github.ronjunevaldoz.awake.compose.ui.layout.composeInto
import io.github.ronjunevaldoz.awake.compose.ui.layout.layoutTree
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.semantics.SemanticsTreeBuilder
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints

/**
 * One UI frame, start to finish.
 *
 * ```
 * dispatchInput(input)   // hit-test LAST frame's placed tree
 * reconcile(root)        // the content lambda runs EXACTLY ONCE
 * measure -> place       // constraints down, sizes up
 * paint                  // -> FrameOutput
 * ```
 *
 * **Input runs before reconcile, not after.** It is resolved against geometry that already exists,
 * which is what removes the one-frame lag immediate mode has -- `ui-core` hit-tests against bounds
 * it is still computing as it goes.
 *
 * The host owns the pointer edge detection, the focus owner and the painter, so a caller does not
 * have to know the order these run in. Getting that order wrong is silent: paint before place and
 * every primitive lands at the previous frame's position.
 */
class ComposeHost(
    private val density: Float = 1f,
    private val fontScale: Float = 1f,
) {
    val root: LayoutNode = LayoutNode(RootMeasurePolicy, density, fontScale)

    private val dispatcher = PointerInputDispatcher()
    private val painter = Painter()
    private val semanticsBuilder = SemanticsTreeBuilder()

    /** Advanced once per frame, before content runs, so every animation in a pass agrees on now. */
    val frameClock: FrameClock = FrameClock()

    /** Shared with the dispatcher, so a Tab key and a click agree on who has focus. */
    val focusOwner: FocusOwner get() = dispatcher.focusOwner

    // Level-to-edge state. A press held for 60 frames is one Press, not 60.
    private var lastPointerDown = false
    private var lastPointerX = FrameInput.UNKNOWN_POINTER
    private var lastPointerY = FrameInput.UNKNOWN_POINTER

    // Nothing has been placed before the first frame, so there is no geometry to hit-test against.
    private var placedOnce = false

    // Reset per frame: "did the UI take this frame's wheel" is a per-frame answer.
    private var scrollConsumed = false

    fun frame(input: FrameInput, content: context(Composer) () -> Unit): FrameOutput {
        scrollConsumed = false
        if (placedOnce) dispatchInput(input)
        // Before content, not after: an animation reads the clock as it is declared, so advancing
        // afterwards would paint every frame one step stale.
        frameClock.advance(input.deltaSeconds)
        composeInto(root) {
            // The constructor's density reaches the *root* node directly, but every other node reads
            // it from the local -- so without this the whole tree below the root laid out at 1x and
            // a HiDPI display rendered half-size, silently.
            CompositionLocalProvider(
                LocalDensity provides density,
                LocalFontScale provides fontScale,
                LocalFrameClock provides frameClock,
                content = content,
            )
        }
        // A node that this pass stopped declaring cannot keep focus, and a modal that just opened
        // takes it from whatever was behind it.
        focusOwner.revalidate(root)
        root.layoutTree(Constraints.fixed(input.viewportWidth, input.viewportHeight))
        placedOnce = true

        // After layout, so a field that only just appeared can still take this frame's typing.
        dispatchText(input)
        val textFocused = focusOwner.focused?.textInputs?.isNotEmpty() == true

        return FrameOutput(
            primitives = painter.paint(root),
            semantics = semanticsBuilder.build(root),
            ownership = InputOwnership(
                isCaptured = dispatcher.hasCapture,
                isOverScrollable = dispatcher.isOverScrollable,
                isScrollConsumed = scrollConsumed,
                isTextInputFocused = textFocused,
                isModalOpen = dispatcher.isModalOpen,
            ),
            // The platform raises a soft keyboard while a field holds focus; on desktop nothing
            // acts on it, which is why it is a request rather than a call.
            effects = PlatformEffects(requestKeyboard = textFocused),
        )
    }

    /**
     * Hands this frame's typing to the focused node, and to nothing else.
     *
     * Routed by focus rather than by pointer position: a keyboard has no coordinates, and the whole
     * point of a caret is that typing goes where it is rather than where the mouse is.
     */
    private fun dispatchText(input: FrameInput) {
        val links = focusOwner.focused?.textInputs ?: return
        if (links.isEmpty()) return
        for (i in links.indices) {
            if (input.typedText.isNotEmpty()) links[i].onTextTyped(input.typedText)
            for (command in input.editCommands) links[i].onEditCommand(command)
        }
    }

    /**
     * Turns this frame's pointer level into the events the dispatcher takes.
     *
     * Move goes first: a press that also moved should hit-test where the pointer *is*, and hover
     * has to settle before a click decides what it landed on.
     */
    private fun dispatchInput(input: FrameInput) {
        val x = input.pointerX
        val y = input.pointerY
        if (x == FrameInput.UNKNOWN_POINTER || y == FrameInput.UNKNOWN_POINTER) {
            // The pointer left the window. Nothing to hit-test, but a held press still has to end,
            // or the node it captured keeps the pointer forever.
            if (lastPointerDown) release(lastPointerX, lastPointerY)
            lastPointerX = FrameInput.UNKNOWN_POINTER
            lastPointerY = FrameInput.UNKNOWN_POINTER
            return
        }

        if (x != lastPointerX || y != lastPointerY) {
            dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), x, y)
        }
        if (input.pointerDown && !lastPointerDown) {
            dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), x, y)
        } else if (!input.pointerDown && lastPointerDown) {
            release(x, y)
        }
        if (input.scrollDeltaY != 0f) {
            scrollConsumed =
                dispatcher.dispatch(root, PointerEvent(PointerEventType.Wheel, input.scrollDeltaY), x, y)
        }

        lastPointerDown = input.pointerDown
        lastPointerX = x
        lastPointerY = y
    }

    private fun release(x: Int, y: Int) {
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), x, y)
        lastPointerDown = false
    }
}

/**
 * Fills the viewport and stacks its children at the origin.
 *
 * The root is the one node whose size is dictated rather than measured -- it is the window. Children
 * get the viewport's constraints loosened, so a child may be smaller but never has to be.
 */
private object RootMeasurePolicy : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val childConstraints = Constraints.of(0, constraints.maxWidth, 0, constraints.maxHeight)
        val placeables = measurables.map { it.measure(childConstraints) }
        return layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { it.placeAt(0, 0) }
        }
    }
}
