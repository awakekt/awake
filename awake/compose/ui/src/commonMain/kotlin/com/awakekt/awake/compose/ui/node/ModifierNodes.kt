/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.node

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.layout.IntrinsicMeasurable
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.layout.onPlaced
import com.awakekt.awake.compose.ui.semantics.SemanticsConfiguration
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Density
import com.awakekt.awake.core.input.ImeComposition
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.core.input.TextEditAction

// Every `*ModifierNode` interface lives here, as in Compose: a modifier's link type is a node
// concern, while `Modifier` itself is only the chain.

/**
 * A link that participates in measurement: it may tighten or loosen what it passes inward, and
 * grow or shrink what it reports outward.
 *
 * [measurable] is the rest of the chain, ending at the node's own [MeasurePolicy]. Measure it
 * exactly once -- the same contract [Measurable] carries.
 */
interface LayoutModifierNode {
    /** Measures [measurable] within [constraints] as adjusted by this link, and returns the size it reports. */
    fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult

    /**
     * How this link changes what the rest of the chain would ask for.
     *
     * Defaults pass straight through, which is right for anything that only paints. A link that
     * changes geometry -- padding, size -- has to override, or an intrinsic query walks past it and
     * reports the inner size as if the link were not there.
     */
    fun Density.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.minIntrinsicWidth(height)

    /** The widest the chain would like to be at the given [height], as seen through this link. */
    fun Density.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.maxIntrinsicWidth(height)

    /** The shortest the chain can be at the given [width], as seen through this link. */
    fun Density.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.minIntrinsicHeight(width)

    /** The tallest the chain would like to be at the given [width], as seen through this link. */
    fun Density.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.maxIntrinsicHeight(width)
}

/**
 * A link that specifies the visual drawing order of this node relative to its siblings.
 */
interface ZIndexModifierNode {
    /** Returns the z-index after this link, given the one [current] accumulated from earlier links. */
    fun modifyZIndex(current: Float): Float
}

/**
 * A link that paints, around whatever the rest of the chain paints.
 *
 * [drawContent] is the rest of the chain plus the node's own children -- call it where the wrapped
 * content should appear. A background calls it last; an overlay calls it first.
 */
interface DrawModifierNode {
    /** Paints into this scope, calling [drawContent] where the wrapped content should appear. */
    fun DrawScope.draw(drawContent: () -> Unit)
}

/**
 * A link told where its node ended up, once placement has resolved it.
 *
 * Replaces reading a container's returned rect, which is what `ui-core` offers and what 46 call
 * sites do today. A returned rect *is* the immediate-mode model -- it can only exist because the
 * container already knows its geometry mid-build -- so the retained engine reports afterwards
 * instead.
 */
interface OnPlacedModifierNode {
    /** Tree-space position and resolved size. Called after every layout pass. */
    fun onPlaced(x: Int, y: Int, width: Int, height: Int)
}

/**
 * A link that hands data upward to the parent's [MeasurePolicy] -- how `weight()` and `align()`
 * reach a Row or Column without the parent reaching into its children.
 */
interface ParentDataModifierNode {
    /** Returns the parent data after this link, given the value [current] from earlier links, or `null`. */
    fun modifyParentData(current: Any?): Any?
}

/** A modifier link that reacts to pointer input. */
interface PointerInputNode {
    /** Handles [event] as it is delivered during one dispatch [pass]. */
    fun onPointerEvent(event: PointerEvent, pass: PointerEventPass)

    /**
     * Extra pixels around this link's own box that still count as hitting it.
     *
     * A control can be thinner than a pointer can reliably land on. A resizable divider is one
     * physical pixel wide because that is what it should look like, and at that width it is not
     * grabbable at all. Compose answers this by growing the *layout* box and centring the visual
     * inside it; shadcn's own handle keeps its `w-px` and overlays a wider strip, so the divider
     * costs the panels one pixel rather than eleven. This is that: hit area only, layout untouched.
     */
    val hitMarginPx: Int get() = 0
}

/** A modifier link that contributes semantics. */
interface SemanticsModifierNode {
    /** The semantics this link contributes to its node. */
    val semanticsConfiguration: SemanticsConfiguration
}

/**
 * A link that can hold focus.
 *
 * Focus is identified by the node rather than by a string, which is the whole change from
 * `UiRuntimeCoordinator`'s single `focusedId: String?`: two widgets could pick the same id and
 * silently steal each other's focus, and there was no tree to derive a traversal order from.
 */
interface FocusTargetNode {
    /** False for a disabled control, which stays in the tree but out of the traversal ring. */
    val canFocus: Boolean

    /** Called when this target gains or loses focus; [focused] is the new state. */
    fun onFocusChanged(focused: Boolean)
}

/**
 * A focus-traversal policy attached to the same node as a focus target.
 *
 * The requester properties name where traversal goes next. Only a
 * [com.awakekt.awake.compose.ui.focus.FocusRequester] is followed; any other value is ignored.
 */
interface FocusPropertiesNode {
    /** Whether the node can take focus in traversal, overriding [FocusTargetNode.canFocus]; `null` defers to it. */
    val canFocusOverride: Boolean?

    /** Where forward traversal goes from this node, or `null` for the default order. */
    val nextRequester: Any?

    /** Where backward traversal goes from this node, or `null` for the default order. */
    val previousRequester: Any?

    /** Where upward traversal goes from this node, or `null` for the default order. */
    val upRequester: Any? get() = null

    /** Where downward traversal goes from this node, or `null` for the default order. */
    val downRequester: Any? get() = null

    /** Where leftward traversal goes from this node, or `null` for the default order. */
    val leftRequester: Any? get() = null

    /** Where rightward traversal goes from this node, or `null` for the default order. */
    val rightRequester: Any? get() = null
}

/**
 * A link that needs a handle on the node it decorates.
 *
 * Rare by design -- most links are told everything they need through their own callback. A focus
 * requester is the exception: it exists to point at a node the caller has no other reference to.
 */
interface NodeAttachedModifierNode {
    /** Called with the [node] this link decorates, each time that node's modifier chain is rebuilt. */
    fun onAttachedTo(node: LayoutNode)
}

/**
 * A link that can consume scroll.
 *
 * Exists so `:ui` can answer "is the pointer over something scrollable" without knowing what a
 * scroller is -- `verticalScroll` lives in `:foundation`, as in Compose. The frame reports it so
 * gameplay knows the wheel was not meant for it.
 */
interface ScrollableNode {
    /** False at both ends, so an outer scrollable can take over at the boundary. */
    val canScroll: Boolean
}

/**
 * Declares the pointer shape to request while this node is hovered.
 *
 * A request the frame reports, not a call: the host collects it into `PlatformEffects` and the
 * platform entry point applies it. Innermost wins, the way CSS `cursor` does -- a resize handle
 * inside a panel shows the resize arrow, not the panel's.
 */
interface PointerCursorNode {
    /** The pointer shape to request while this node is hovered. */
    val cursor: PointerCursor
}

/**
 * A link that accepts keyboard text.
 *
 * The seam that lets `:ui` route typing without knowing what a text field is -- `BasicTextField`
 * lives in `:foundation`, as in Compose. Its presence on the focused node is also what makes
 * `FrameOutput.ownership.isTextInputFocused` true, which is how gameplay knows W/A/S/D is being
 * typed rather than walking the player.
 */
interface TextInputNode {
    /**
     * Whether this node edits a password: it masks what it draws, and the platform should tell the
     * IME so the keyboard neither learns nor suggests the text. Reported through
     * `PlatformEffects.passwordKeyboard` while the node holds focus.
     */
    val isPassword: Boolean get() = false

    /** Receives [text] typed on the keyboard. */
    fun onTextTyped(text: String)

    /** Receives a discrete editing command such as backspace or a cursor move. */
    fun onEditAction(action: TextEditAction)

    /** Receives the IME's uncommitted [composition]; ignored unless overridden. */
    fun onImeComposition(composition: ImeComposition) = Unit

    /** Receives [text] the IME has finished composing; by default it is treated as typed text. */
    fun onImeCommit(text: String) = onTextTyped(text)

    /**
     * The text a copy command should place on the clipboard, or `null` for nothing.
     *
     * The single path every clipboard adapter must take, so a field can refuse: a password field
     * answers `null`, as a browser does for `<input type=password>`.
     */
    fun copySelection(): String? = null

    /** Like [copySelection], and removes what it returns from the field. `null` leaves the text alone. */
    fun cutSelection(): String? = null
}

/**
 * A link that reacts to a key.
 *
 * Routed by **focus**, not by pointer position -- a keyboard has no coordinates. Delivered along the
 * path from the root to the focused node, so an ancestor can take a key before the focused node
 * sees it: a dialog swallowing Escape before the field inside it reads it as "clear selection".
 */
interface KeyInputNode {
    /** Handles [event] as it travels the focus path during one [pass]. */
    fun onKeyEvent(event: KeyEvent, pass: KeyEventPass)
}
