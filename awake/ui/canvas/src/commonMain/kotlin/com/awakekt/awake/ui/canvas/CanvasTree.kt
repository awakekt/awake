/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.localization.Strings
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.core.math2d.contains
import com.awakekt.awake.core.text.font.UiFont

/**
 * One drawn [CanvasElement], carrying what [CanvasOverlay] needs but a host's own object graph
 * would otherwise hold. A follower is always a root regardless of [parent]: it is placed on what
 * it follows, not inside a box.
 *
 * @property id Its `canvas-element-<id>` test tag.
 * @property element The element to draw.
 * @property parent The nearest element above it the host found there, or null for a root.
 */
class CanvasItem(val id: Int, val element: CanvasElement, val parent: CanvasElement? = null)

/**
 * What [CanvasOverlay] asks its host for: a [CanvasElement.follow]ed node's projected point or
 * bounds, a View's camera picture, and a [CanvasElement.bind]'s field text. Every member answers
 * null, so a host that implements none of what an element asks for still draws it, minus that one
 * thing.
 */
interface CanvasResolver {
    /** The followed node named [target]'s screen point, in frame pixels, offset by [offset] in world space. */
    fun point(target: String, offset: Vec3f): Vec2? = null

    /** The frame rectangle the followed node named [target] covers. */
    fun bounds(target: String): Rectangle? = null

    /**
     * The followed node named [target]'s point, offset by [offset] in world space, with how far it is
     * from the camera and whether it is behind it, for [CanvasFollowRules]' culling, ranking, curves
     * and edge pinning. By default it is [point], at no known distance, and never behind: a point
     * behind the camera is one [point] gives no answer for.
     */
    fun project(target: String, offset: Vec3f): CanvasProjection? = point(target, offset)?.let { CanvasProjection(it.x, it.y) }

    /**
     * Whether something stands between the camera and the followed node named [target]'s point,
     * offset by [offset]: asked only for an element whose [CanvasFollowRules.occlusion] is set, and
     * only while the point is on screen. False, the default, hides none.
     */
    fun occluded(target: String, offset: Vec3f): Boolean = false

    /** A View's camera picture: what the camera named [camera] sees, at [width] by [height] pixels. */
    fun texture(element: CanvasElement, camera: String, width: Int, height: Int): Any? = null

    /** A bound field's raw text on the node named [node], addressed as `component.field`. */
    fun field(node: String, path: String): String? = null

    /** Holds [None]. */
    companion object {
        /** A resolver with nothing behind it: every member answers null. */
        val None: CanvasResolver = object : CanvasResolver {}
    }
}

/**
 * [items], each under the nearest element above it in the scene hierarchy. One that follows a node
 * is placed against the screen wherever it sits in the scene, by where [resolver] puts the node in
 * the frame less where the canvas sits in it, [origin].
 */
internal class CanvasTree(
    val items: List<CanvasItem>,
    private val showTouchControls: Boolean,
    val images: Map<String, ImageBitmap>,
    val resolver: CanvasResolver,
    val origin: CanvasOrigin = CanvasOrigin(),
    private val fonts: Map<String, UiFont> = emptyMap(),
    private val strings: Strings? = null,
    val controls: CanvasControls = CanvasControls.Default,
    val tooltips: CanvasTooltips = CanvasTooltips(),
) {
    private val hierarchy = CanvasHierarchy(items)

    val roots: List<CanvasItem> get() = hierarchy.roots

    init {
        // Cleared up front so an element not composed this frame -- hidden, or a follower with
        // nowhere to land -- reports null rather than where it last was.
        for (item in items) {
            val element = item.element
            element.screenBounds = null
            // A control hidden mid-press, by its own visibility or its parent's, lets go of its
            // pointer, so a stick does not stay deflected while the controls are hidden.
            val parent = item.parent
            if (!shows(element) || parent != null && !shows(parent)) element.dropPointers()
        }
    }

    fun childrenOf(element: CanvasElement): List<CanvasItem> = hierarchy.childrenOf(element)

    /** Where each follower drawn this frame goes, as [planFollowers] last worked it out; one not in it is not drawn. */
    internal val followerPlans = HashMap<CanvasElement, FollowerPlan>()

    fun shows(element: CanvasElement): Boolean = element.visible && (showTouchControls || !element.touchOnly)

    /** The font an element's words draw in: its loaded file or built-in family, or null for the default. */
    fun fontOf(element: CanvasElement): UiFont? = canvasFont(element.font, fonts)

    /** A Bar's fill: what its binding reads, or its own value when it has none or it reads nothing. */
    fun valueOf(element: CanvasElement): Float {
        val bind = element.bind?.takeIf { it.value.isNotBlank() } ?: return element.value
        val value = resolver.numberField(bind.node, bind.value)
        val max = if (bind.max.isBlank()) 1f else resolver.numberField(bind.node, bind.max)
        return if (value == null || max == null || max <= 0f) element.value else (value / max).coerceIn(0f, 1f)
    }

    /**
     * An element's words: its key's text in the active locale, else its binding's text filled in, or
     * its own text when it has neither.
     */

    /**
     * Writes an action button's bound [CanvasBinding.cooldown], [CanvasBinding.charges] and
     * [CanvasBinding.enabled] into [element], so game code and the runtime read the same live values
     * a system setting them would have. A field that reads nothing leaves the element as it is.
     */
    fun readButtonState(element: CanvasElement) {
        val bind = element.bind ?: return
        if (bind.cooldown.isNotBlank()) {
            val left = resolver.numberField(bind.node, bind.cooldown)
            val max = if (bind.cooldownMax.isBlank()) 1f else resolver.numberField(bind.node, bind.cooldownMax)
            if (left != null && max != null && max > 0f) element.cooldown = left / max
        }
        if (bind.charges.isNotBlank()) resolver.numberField(bind.node, bind.charges)?.let { element.charges = it.toInt() }
        if (bind.enabled.isNotBlank()) resolver.numberField(bind.node, bind.enabled)?.let { element.enabled = it > 0f }
    }

    fun textOf(element: CanvasElement): String {
        val bind = element.bind?.takeIf { it.text.isNotBlank() }
        return when {
            element.textKey.isNotEmpty() -> keyedText(element.textKey, element)
            bind == null -> element.text
            else -> resolver.textField(bind.node, bind.text)
        }
    }

    /** [key] in the active locale, with [element]'s arguments; the key itself without [strings]. */
    internal fun keyedText(key: String, element: CanvasElement): String {
        val strings = strings ?: return key.also(::warnNoStrings)
        return strings.text(key, argumentsOf(element))
    }

    /** [element]'s own [CanvasElement.textArgs], with each its binding reads from a field in place of its own. */
    private fun argumentsOf(element: CanvasElement): Map<String, Any?> {
        val bind = element.bind?.takeIf { it.textArgs.isNotEmpty() } ?: return element.textArgs
        val arguments = HashMap<String, Any?>(element.textArgs)
        for ((name, path) in bind.textArgs) resolver.field(bind.node, path)?.let { arguments[name] = it }
        return arguments
    }

    /**
     * The drop target, other than [exclude], drawn topmost at canvas point ([x], [y]) by its last
     * placed box, or null: a child over its parent, a later sibling over an earlier one.
     */
    fun dropTargetAt(x: Float, y: Float, exclude: CanvasElement): CanvasElement? = dropTargetIn(roots, x, y, exclude)

    private fun dropTargetIn(entries: List<CanvasItem>, x: Float, y: Float, exclude: CanvasElement): CanvasElement? {
        var found: CanvasElement? = null
        for (item in entries) {
            val element = item.element
            if (!shows(element)) continue
            val here = element !== exclude && element.dropTarget && element.screenBounds?.contains(x, y) == true
            found = dropTargetIn(childrenOf(element), x, y, exclude) ?: (if (here) element else found)
        }
        return found
    }
}

private val keyLog = Logger("ui-canvas")

// The keys drawn with no strings to look them up in: as many as the scenes name, so it stays small.
private val keysWithoutStrings = HashSet<String>()

private fun warnNoStrings(key: String) {
    if (keysWithoutStrings.add(key)) {
        keyLog.warn { "A canvas element shows the text key '$key', but the canvas was given no strings to look it up in, so it shows the key" }
    }
}
