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
import com.awakekt.awake.compose.ui.focus.applyPendingFocusRequests
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
import com.awakekt.awake.core.input.ClipboardCommand
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
 * which is what removes the one-frame lag immediate mode has -- the removed `ui-core` hit-tested against
 * bounds it was still computing as it went.
 *
 * The host owns the pointer edge detection, the focus owner and the painter, so a caller does not
 * have to know the order these run in. Getting that order wrong is silent: paint before place and
 * every primitive lands at the previous frame's position.
 */
@Suppress("TooManyFunctions") // One frame pipeline (input, compose, layout, paint) plus its clipboard and disposal.
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

    /**
     * The direction start and end resolve against.
     *
     * Setting it updates [root] at once, and composition sees it through
     * [com.awakekt.awake.compose.ui.unit.LocalLayoutDirection] on the next frame.
     */
    var layoutDirection: com.awakekt.awake.compose.ui.unit.LayoutDirection = layoutDirection
        set(value) {
            field = value
            root.layoutDirection = value
        }

    /**
     * What covers the window's edges, which composition sees through [LocalWindowInsets] on the
     * next frame. A host sets it when the platform reports a change, not every frame.
     */
    var windowInsets: WindowInsets = WindowInsets.None

    /** The root layout node. It fills the viewport, and the composed content becomes its children. */
    val root: LayoutNode = LayoutNode(RootMeasurePolicy, density, fontScale, layoutDirection)
    private val composition = LayoutComposition(root)

    private val dispatcher = PointerInputDispatcher()
    private val painter = Painter()
    private val semanticsBuilder = SemanticsTreeBuilder()
    private val keyDispatcher = KeyInputDispatcher(dispatcher.focusOwner)

    /** Advanced once per frame, before content runs, so every animation in a pass agrees on now. */
    val frameClock: FrameClock = FrameClock()

    /**
     * Skips the composition pass of a frame in which nothing it read has changed. Off by default.
     *
     * When on, [frame] runs [content] only if a [com.awakekt.awake.compose.runtime.State] the last
     * pass read was written with a different value, a polled state it read now differs, the frame
     * clock moved while the pass read it, or the density, viewport, window insets, pointer modifiers
     * or layout direction differ from the last composed pass. Measure, place, paint, input, focus and
     * semantics run every frame either way, so the output is the same as with it off **provided the
     * content reads only tracked state and the values above**. A content lambda that reads a plain
     * `var` another frame changed would show the old value: keep that data in `mutableStateOf`, call
     * [invalidate] or answer [externalChanges] when it changes, or leave this off. Content must not
     * rely on running every frame. [verifySkippedCompositions] finds reads the host does not observe.
     */
    var skipUnchangedComposition: Boolean = false
        set(value) {
            field = value
            // Plain holders (hover, press, focus counts, the clock) only create tracking state for
            // a host that skips, so the default path allocates what it always did.
            composition.tracksChanges = value
        }

    /** How many frames [skipUnchangedComposition] spared a composition pass. */
    var skippedCompositions: Int = 0
        private set

    /**
     * Asked once a frame, before deciding to skip, whether state the content reads outside the
     * snapshot system changed: an ECS field, a plain `var`, a list a system rewrites. Returning
     * `true` makes this frame compose, as [invalidate] does. A consumer whose UI state lives in
     * such fields sets it to a check its own systems already know the answer to, and can then turn
     * [skipUnchangedComposition] on (#1544). Not called while [skipUnchangedComposition] is off.
     */
    var externalChanges: (() -> Boolean)? = null

    /**
     * Debug aid for [skipUnchangedComposition]: on a frame it would skip, compose anyway and compare
     * the paint output with what the skipped frame would have drawn. A difference means the content
     * read something the host does not observe; it counts in [staleSkips] and is reported to
     * [onStaleSkip]. The frame shows the composed output, so this behaves like skipping off. Costs
     * a second paint per frame: use it in tests and debug builds.
     */
    var verifySkippedCompositions: Boolean = false

    /** Told of each stale skip [verifySkippedCompositions] found, with the first differing primitive. */
    var onStaleSkip: ((StaleSkip) -> Unit)? = null

    /** Frames [verifySkippedCompositions] found would have drawn stale output had they skipped. */
    var staleSkips: Int = 0
        private set

    private var externallyInvalid = false

    /**
     * Marks state read outside the snapshot system as changed: the next frame composes even if
     * [skipUnchangedComposition] would skip it. Cheap and idempotent; call it from wherever that
     * state changes, or answer through [externalChanges] once a frame.
     */
    fun invalidate() {
        externallyInvalid = true
    }

    private val compositionInputs = CompositionInputs()

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
    private var imeOwner: LayoutNode? = null

    // Reset per frame: "did the UI take this frame's wheel" is a per-frame answer.
    private var scrollConsumed = false

    /**
     * Runs one frame for [input], composing [content], and returns what to draw and what the platform
     * should do.
     *
     * Pointer input is resolved against the previous frame's placed tree, so the first frame
     * dispatches none. Typed text and key events are dispatched after layout, so a field that only
     * just appeared can still take them.
     */
    fun frame(input: FrameInput, content: context(Composer) () -> Unit): FrameOutput {
        scrollConsumed = false
        // Before input, as documented: a `FocusRequester.requestFocus()`/`freeFocus()` queued
        // since the last frame -- typically from outside composition entirely -- takes effect
        // before this frame's click or key can see a stale focus owner.
        focusOwner.applyPendingFocusRequests(root)
        if (placedOnce) dispatchInput(input)
        // Before content, not after: an animation reads the clock as it is declared, so advancing
        // afterwards would paint every frame one step stale.
        frameClock.advance(input.deltaSeconds)
        if (skipUnchangedComposition && !needsComposition(input)) {
            if (verifySkippedCompositions) verifySkip(content, input) else skippedCompositions++
        } else if (compositionStats.enabled) {
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
        val focusedTextInputs = focusOwner.focused?.textInputs.orEmpty()
        val textFocused = focusedTextInputs.isNotEmpty()
        val passwordFocused = focusedTextInputs.any { it.isPassword }
        // Blocked only if EVERY focused input is single-line: one multi-line field among them still
        // needs Up/Down for its own caret.
        val singleLineFocused = focusedTextInputs.all { it.isSingleLine }
        val passThroughKeys = focusedTextInputs.flatMapTo(mutableSetOf()) { it.passThroughKeys }
        val clipboardText = answerClipboardCommands(input.clipboardCommands)

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
                isTextInputSingleLine = singleLineFocused,
                textInputPassThroughKeys = passThroughKeys,
            ),
            // The platform raises a soft keyboard while a field holds focus; on desktop nothing
            // acts on it, which is why it is a request rather than a call.
            effects = PlatformEffects(
                requestKeyboard = textFocused,
                cursor = dispatcher.hoveredCursor,
                cursorImage = dispatcher.hoveredCursorImage,
                passwordKeyboard = passwordFocused,
                clipboardText = clipboardText,
            ),
        )
    }

    /**
     * Drops the composed tree: every node goes, and every remembered
     * [com.awakekt.awake.compose.runtime.RememberObserver] is told it was forgotten, so a store or
     * scope the UI remembered is released. Call it when the host stops being drawn, before the
     * renderer it drew with goes away.
     *
     * Disposing twice is harmless. A [frame] after this composes from an empty tree, as the first
     * frame did.
     */
    fun dispose() {
        composition.dispose()
        compositionInputs.reset()
        placedOnce = false
        imeOwner = null
    }

    /**
     * What a copy command (Ctrl/Cmd+C, a context menu) should place on the clipboard: the focused
     * field's selection, or `null` when nothing is focused, nothing is selected, or the field
     * refuses -- a password field always does.
     *
     * A clipboard adapter reads from here rather than from a `TextFieldState`: the state holds the
     * real text and cannot know that the field drawing it is masked.
     */
    fun copyFocusedSelection(): String? = focusOwner.focused?.textInputs.orEmpty().firstNotNullOfOrNull {
        it.copySelection()
    }

    /** The last of [commands] that produced text wins, as a second Ctrl+C overwrites the first. */
    private fun answerClipboardCommands(commands: List<ClipboardCommand>): String? {
        var answer: String? = null
        for (i in commands.indices) {
            val text = when (commands[i]) {
                ClipboardCommand.Copy -> copyFocusedSelection()
                ClipboardCommand.Cut -> cutFocusedSelection()
            }
            if (text != null) answer = text
        }
        return answer
    }

    /** Like [copyFocusedSelection], and removes the returned text from the field. */
    fun cutFocusedSelection(): String? = focusOwner.focused?.textInputs.orEmpty().firstNotNullOfOrNull {
        it.cutSelection()
    }

    private fun needsComposition(input: FrameInput): Boolean {
        if (externalChanges?.invoke() == true) externallyInvalid = true
        return externallyInvalid || composition.isInvalid ||
            compositionInputs.differsFrom(density, input, windowInsets, layoutDirection)
    }

    /** [verifySkippedCompositions]: paints the tree as the skipped frame would, composes, and compares. */
    private fun verifySkip(content: context(Composer) () -> Unit, input: FrameInput) {
        root.layoutTree(Constraints.fixed(input.viewportWidth, input.viewportHeight))
        val skipped = painter.paintOutput(root).primitives.toList()
        compose(content, input)
        root.layoutTree(Constraints.fixed(input.viewportWidth, input.viewportHeight))
        val composed = painter.paintOutput(root).primitives
        val index = firstDifference(skipped, composed)
        if (index >= 0) {
            staleSkips++
            onStaleSkip?.invoke(StaleSkip(index, skipped.getOrNull(index), composed.getOrNull(index)))
        }
    }

    private fun compose(content: context(Composer) () -> Unit, input: FrameInput): Composer {
        externallyInvalid = false
        compositionInputs.record(density, input, windowInsets, layoutDirection)
        return composeContent(content, input)
    }

    private fun composeContent(content: context(Composer) () -> Unit, input: FrameInput): Composer =
        composition.compose {
            // The constructor's density reaches the *root* node directly, but every other node reads
            // it from the local -- so without this the whole tree below the root laid out at 1x and
            // a HiDPI display rendered half-size, silently.
            CompositionLocalProvider(
                LocalDensity provides density,
                LocalFontScale provides fontScale,
                LocalFrameClock provides frameClock,
                LocalViewportSize provides ViewportSize(input.viewportWidth, input.viewportHeight),
                LocalWindowInsets provides windowInsets,
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
        val focused = focusOwner.focused
        // The IME can finish an old composition after focus has already moved to another field.
        input.imeCommit?.let { text ->
            (imeOwner ?: focused)?.textInputs?.forEach { it.onImeCommit(text) }
            imeOwner = null
        }
        input.imeComposition?.let { composition ->
            val owner = imeOwner ?: focused?.takeIf { it.textInputs.isNotEmpty() }
            imeOwner = owner
            owner?.textInputs?.forEach { it.onImeComposition(composition) }
        }
        val links = focused?.textInputs ?: return
        for (i in links.indices) {
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
