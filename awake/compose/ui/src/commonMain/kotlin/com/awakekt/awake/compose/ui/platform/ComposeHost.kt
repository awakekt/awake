/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.focus.FocusOwner
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.input.key.KeyInputDispatcher
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.input.pointer.PointerInputDispatcher
import com.awakekt.awake.compose.ui.input.pointer.PointerModifiers
import com.awakekt.awake.compose.ui.layout.LayoutComposition
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.node.activeEscapeDismissLayer
import com.awakekt.awake.compose.ui.node.activeModalLayer
import com.awakekt.awake.compose.ui.semantics.SemanticsTreeBuilder
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.core.input.Key
import kotlin.time.TimeSource

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
    density: Float = 1f,
    private val fontScale: Float = 1f,
    layoutDirection: com.awakekt.awake.compose.ui.unit.LayoutDirection =
        com.awakekt.awake.compose.ui.unit.LayoutDirection.Ltr,
) {
    /** Physical pixels per device-independent unit. A `var`, not the constructor's fixed value:
     * a host's real display scale is often unknown until the window exists, which is after this
     * class is constructed (see `SceneAppLifecycleRuntime.uiHost`'s own doc comment). Kept in
     * sync with [root]'s own density field, which every layout measurement actually reads. */
    var density: Float = density
        set(value) {
            field = value
            root.density = value
        }

    var layoutDirection: com.awakekt.awake.compose.ui.unit.LayoutDirection = layoutDirection
        set(value) {
            field = value
            root.layoutDirection = value
        }

    val root: LayoutNode = LayoutNode(RootMeasurePolicy, density, fontScale, layoutDirection)
    private val composition = LayoutComposition(root)

    private val dispatcher = PointerInputDispatcher()
    private val painter = Painter()
    private val semanticsBuilder = SemanticsTreeBuilder()
    private val keyDispatcher = KeyInputDispatcher(dispatcher.focusOwner)

    /** Advanced once per frame, before content runs, so every animation in a pass agrees on now. */
    val frameClock: FrameClock = FrameClock()

    /** Disabled-by-default composition attribution for Stage 2 profiling. */
    val compositionStats: ComposeFrameStats = ComposeFrameStats()

    /** Shared with the dispatcher, so a Tab key and a click agree on who has focus. */
    val focusOwner: FocusOwner get() = dispatcher.focusOwner

    // Level-to-edge state. A press held for 60 frames is one Press, not 60.
    private var lastPointerDown = false
    private var lastPointerX = FrameInput.UNKNOWN_POINTER
    private var lastPointerY = FrameInput.UNKNOWN_POINTER
    private val touchPointers = mutableMapOf<Long, PointerFrame>()
    private val seenTouchPointers = mutableSetOf<Long>()
    private val disappearedTouchPointers = mutableListOf<PointerFrame>()

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
        if (compositionStats.enabled) {
            val started = TimeSource.Monotonic.markNow()
            compose(content, input)
            compositionStats.recordComposition(started.elapsedNow().inWholeNanoseconds)
        } else {
            compose(content, input)
        }
        // A node that this pass stopped declaring cannot keep focus, and a modal that just opened
        // takes it from whatever was behind it.
        focusOwner.revalidate(root)
        root.layoutTree(Constraints.fixed(input.viewportWidth, input.viewportHeight))
        placedOnce = true

        // After layout, so a field that only just appeared can still take this frame's typing.
        dispatchText(input)
        dispatchKeys(input)
        val textFocused = focusOwner.focused?.textInputs?.isNotEmpty() == true

        val paint = painter.paintOutput(root)
        return FrameOutput(
            primitives = paint.primitives,
            graphicsLayers = paint.layers,
            semantics = semanticsBuilder.build(root),
            ownership = InputOwnership(
                isCaptured = dispatcher.hasCapture,
                isOverScrollable = dispatcher.isOverScrollable,
                isScrollConsumed = scrollConsumed,
                isTextInputFocused = textFocused,
                isModalOpen = root.activeModalLayer() != null || dispatcher.isModalOpen,
            ),
            // The platform raises a soft keyboard while a field holds focus; on desktop nothing
            // acts on it, which is why it is a request rather than a call.
            effects = PlatformEffects(requestKeyboard = textFocused, cursor = dispatcher.hoveredCursor),
        )
    }

    private fun compose(content: context(Composer) () -> Unit, input: FrameInput): Composer =
        composition.compose {
            // The constructor's density reaches the *root* node directly, but every other node reads
            // it from the local -- so without this the whole tree below the root laid out at 1x and
            // a HiDPI display rendered half-size, silently.
            CompositionLocalProvider(
                LocalDensity provides density,
                LocalFontScale provides fontScale,
                LocalFrameClock provides frameClock,
                LocalViewportSize provides ViewportSize(input.viewportWidth, input.viewportHeight),
                LocalPointerModifiers provides input.pointerModifiers,
                com.awakekt.awake.compose.ui.unit.LocalLayoutDirection provides layoutDirection,
                content = content,
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
            input.imeComposition?.let(links[i]::onImeComposition)
            input.imeCommit?.let(links[i]::onImeCommit)
            if (input.typedText.isNotEmpty()) links[i].onTextTyped(input.typedText)
            for (command in input.editActions) links[i].onEditAction(command)
        }
    }

    /**
     * Routes this frame's keys along the focus path, then lets Tab move focus.
     *
     * Traversal runs last and only on an unconsumed Tab, so a node that wants the key for itself --
     * an editor inserting an indent -- takes it and the ring stays put.
     */
    private fun dispatchKeys(input: FrameInput) {
        val events = input.keyEvents
        for (i in events.indices) {
            val event = events[i]
            if (event.type == KeyEventType.Down && event.key == Key.Escape) {
                root.activeEscapeDismissLayer()?.onDismissRequest?.let { dismiss ->
                    dismiss()
                    event.consume()
                }
            }
            keyDispatcher.dispatch(root, event)
            keyDispatcher.handleFocusTraversal(root, event)
        }
    }

    /**
     * Turns this frame's pointer level into the events the dispatcher takes.
     *
     * Move goes first: a press that also moved should hit-test where the pointer *is*, and hover
     * has to settle before a click decides what it landed on.
     */
    private fun dispatchInput(input: FrameInput) {
        dispatchTouchInput(input)
        val x = input.pointerX
        val y = input.pointerY
        // One read per frame, shared by every pointer event it dispatches: they all happened
        // at the same instant, so they all saw the same modifiers.
        val mods = input.pointerModifiers
        if (x == FrameInput.UNKNOWN_POINTER || y == FrameInput.UNKNOWN_POINTER) {
            // The pointer left the window. Nothing to hit-test, but a held press still has to end,
            // or the node it captured keeps the pointer forever.
            if (lastPointerDown) release(lastPointerX, lastPointerY, mods)
            lastPointerX = FrameInput.UNKNOWN_POINTER
            lastPointerY = FrameInput.UNKNOWN_POINTER
            return
        }

        if (x != lastPointerX || y != lastPointerY) {
            // First move after the pointer appears has no previous position to difference against;
            // a delta measured from UNKNOWN_POINTER would be an enormous jump on the first frame.
            val known = lastPointerX != FrameInput.UNKNOWN_POINTER
            dispatcher.dispatch(
                root,
                PointerEvent(PointerEventType.Move, modifiers = mods),
                x,
                y,
                dx = if (known) x - lastPointerX else 0,
                dy = if (known) y - lastPointerY else 0,
            )
        }
        if (input.pointerPressed || (input.pointerDown && !lastPointerDown)) {
            dispatcher.dispatch(root, PointerEvent(PointerEventType.Press, modifiers = mods), x, y)
        }
        // After the primary press so a context menu opening on this frame is not immediately
        // dismissed by a primary press that also arrived, and before Release for the same reason.
        if (input.secondaryPointerPressed) {
            dispatcher.dispatch(root, PointerEvent(PointerEventType.SecondaryPress, modifiers = mods), x, y)
        }
        if (input.pointerDown) dispatcher.advanceTime(root, x, y, input.deltaSeconds)
        if (input.pointerReleased || (!input.pointerDown && lastPointerDown)) {
            release(x, y, mods)
        }
        if (input.scrollDeltaY != 0f) {
            scrollConsumed =
                dispatcher.dispatch(root, PointerEvent(PointerEventType.Wheel, input.scrollDeltaY, modifiers = mods), x, y)
        }

        lastPointerDown = input.pointerDown
        lastPointerX = x
        lastPointerY = y
    }

    /** Routes every active touch independently; the mouse remains pointer id zero. */
    private fun dispatchTouchInput(input: FrameInput) {
        val mods = input.pointerModifiers
        seenTouchPointers.clear()
        for (touch in input.pointers) {
            require(touch.pointerId != 0L) { "PointerFrame id 0 is reserved for FrameInput's mouse pointer." }
            require(seenTouchPointers.add(touch.pointerId)) { "FrameInput contains duplicate pointer id ${touch.pointerId}." }
            val previous = touchPointers[touch.pointerId]
            if (previous != null && (previous.x != touch.x || previous.y != touch.y)) {
                dispatcher.dispatch(
                    root,
                    PointerEvent(PointerEventType.Move, pointerId = touch.pointerId, modifiers = mods),
                    touch.x,
                    touch.y,
                    dx = touch.x - previous.x,
                    dy = touch.y - previous.y,
                )
            }
            if (touch.pressed || (touch.down && previous?.down != true)) {
                dispatcher.dispatch(root, PointerEvent(PointerEventType.Press, pointerId = touch.pointerId, modifiers = mods), touch.x, touch.y)
            }
            if (touch.down) dispatcher.advanceTime(root, touch.x, touch.y, input.deltaSeconds, touch.pointerId)
            if (touch.released || (!touch.down && previous?.down == true)) {
                dispatcher.dispatch(root, PointerEvent(PointerEventType.Release, pointerId = touch.pointerId, modifiers = mods), touch.x, touch.y)
            }
            if (touch.down) touchPointers[touch.pointerId] = touch else touchPointers.remove(touch.pointerId)
        }
        // A platform normally includes every active contact. If one vanishes unexpectedly, release
        // its capture at the last known position rather than stranding a drag indefinitely.
        disappearedTouchPointers.clear()
        for ((pointerId, touch) in touchPointers) {
            if (pointerId !in seenTouchPointers) disappearedTouchPointers += touch
        }
        for (touch in disappearedTouchPointers) {
            dispatcher.dispatch(root, PointerEvent(PointerEventType.Release, pointerId = touch.pointerId, modifiers = mods), touch.x, touch.y)
            touchPointers.remove(touch.pointerId)
        }
    }

    private fun release(x: Int, y: Int, modifiers: PointerModifiers) {
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release, modifiers = modifiers), x, y)
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
