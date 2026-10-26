/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.color.toHex
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.Sp
import com.awakekt.awake.core.text.unicode.TextLengthUnit
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.scene.document.SceneValidationException
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasBar
import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.CanvasButton
import com.awakekt.awake.ui.canvas.CanvasCluster
import com.awakekt.awake.ui.canvas.CanvasControlSkin
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasFollowRules
import com.awakekt.awake.ui.canvas.CanvasJoystick
import com.awakekt.awake.ui.canvas.CanvasLayout
import com.awakekt.awake.ui.canvas.CanvasLook
import com.awakekt.awake.ui.canvas.CanvasPadding
import com.awakekt.awake.ui.canvas.CanvasStepper
import com.awakekt.awake.ui.canvas.CanvasStyle
import com.awakekt.awake.ui.canvas.CanvasSweep

/** Keeps an outer element's settings out of reach inside a child's block. */
@DslMarker
annotation class CanvasPrefabDsl

/**
 * A prefab of `canvas_element` nodes built in code: the same [ScenePrefab] an editor saves, so its
 * JSON, from [CanvasPrefabs.toJson], opens in the editor as it is and saves back unchanged.
 *
 * The block sets up the root element, of [kind], named [name], and adds its children. The prefab
 * is validated as [CanvasPrefabs.problems] does before it is returned.
 *
 * ```kotlin
 * val frame = canvasPrefab(guid = "6f1c...", name = "Unit Frame") {
 *     size(240.dp, 56.dp)
 *     background = Color(0f, 0f, 0f, 0.6f)
 *     text("Name", id = "name") { textKey = "hud.unit.name"; fontSize = 14.sp }
 *     bar("Health", id = "health") {
 *         offset(8.dp, 28.dp)
 *         size(224.dp, 12.dp)
 *         bind = CanvasBinding(node = "Player", value = "health.current", max = "health.max")
 *     }
 * }
 * ```
 *
 * @param guid The prefab's GUID, which a scene's `prefab_link` and an editor know it by. Keep it the
 * same from one build to the next, so the file diffs cleanly.
 * @param name The prefab's name, which is its root node's name too.
 * @param kind What the root element draws; a [CanvasElementKind.Panel] by default.
 * @param id The root element's [CanvasElementScope.elementId]; empty for none.
 * @param block Sets up the root element and adds its children.
 * @return The validated prefab.
 * @throws SceneValidationException when the prefab has a problem, listing each one.
 */
fun canvasPrefab(
    guid: String,
    name: String,
    kind: CanvasElementKind = CanvasElementKind.Panel,
    id: String = "",
    block: CanvasElementScope.() -> Unit,
): ScenePrefab {
    val prefab = ScenePrefab(guid = guid, name = name, root = CanvasElementScope(name, kind, id).apply(block).build())
    val problems = CanvasPrefabs.problems(prefab)
    if (problems.isNotEmpty()) throw SceneValidationException(problems)
    return prefab
}

/**
 * One `canvas_element` node of a prefab being built: its settings, typed, and the elements inside
 * it, added with [text], [panel], [bar], [button] and the other kinds' functions.
 *
 * Each setting is the `canvas_element` field of the same name, starting at that field's default.
 * Sizes and offsets are [Dp], a font size is [Sp], and colours are [Color]s, written as
 * [Color.toHex] writes them. Settings that are objects of their own, such as [style], [layout],
 * [bind] or [button], take the `awake:ui:canvas` type the field holds.
 *
 * @param name The node's name.
 * @property kind What the element draws.
 * @param id The element's [elementId].
 */
@CanvasPrefabDsl
@Suppress("TooManyFunctions") // One function for each element kind.
class CanvasElementScope internal constructor(
    private val name: String,
    val kind: CanvasElementKind,
    id: String,
) {
    private val defaults = SceneCanvasElement(kind = kind)
    private val children = mutableListOf<SceneNode>()
    private val components = mutableListOf<SceneComponent>()

    /** The stable id code finds this element by; see [SceneCanvasElement.elementId]. */
    var elementId: String = id

    /** The screen point, or the parent's, the element is pinned to. */
    var anchor: CanvasAnchor = defaults.anchor

    /** How far the element moves from its anchor along the X axis; see [SceneCanvasElement.offsetX]. */
    var offsetX: Dp = Dp(defaults.offsetX)

    /** How far the element moves from its anchor along the Y axis; see [SceneCanvasElement.offsetY]. */
    var offsetY: Dp = Dp(defaults.offsetY)

    /** The element's width. */
    var width: Dp = Dp(defaults.width)

    /** The element's height. */
    var height: Dp = Dp(defaults.height)

    /** A Text's words or a Button's label, typed in; prefer a [textKey] for words a player reads. */
    var text: String = defaults.text

    /** The key of the words in the project's string tables; see [SceneCanvasElement.textKey]. */
    var textKey: String = defaults.textKey

    /** Values for [textKey]'s placeholders, by name. */
    var textArgs: Map<String, String> = defaults.textArgs

    /** The size of the element's words. */
    var fontSize: Sp = Sp(defaults.fontSize)

    /** The colour of the element's words, fill or knob. */
    var color: Color = Color.fromHex(defaults.color)

    /** The colour behind the element. */
    var background: Color = Color.fromHex(defaults.background)

    /** A Bar's or a Sweep's fill, from 0 to 1. */
    var value: Float = defaults.value

    /** The element's draw order among the elements beside it: a higher one draws on top. */
    var order: Int = defaults.order

    /** Whether the element is drawn. */
    var visible: Boolean = defaults.visible

    /** What the element does for the game when pressed, such as `jump`. */
    var action: String = defaults.action

    /** What a Button does when double-clicked. */
    var doubleAction: String = defaults.doubleAction

    /** Whether the element is drawn only while touch controls are shown. */
    var touchOnly: Boolean = defaults.touchOnly

    /** Whether an anchored element keeps inside the safe area. */
    var safeArea: Boolean = defaults.safeArea

    /** How the element looks beyond its colours: images, nine-slice frames, fills, borders and states. */
    var style: CanvasStyle = defaults.style

    /** Where a Text's or a Button's words sit in the element; null keeps the kind's own. */
    var textAlign: CanvasAnchor? = defaults.textAlign

    /** The name of the node the element follows on screen; empty follows none. */
    var follow: String = defaults.follow

    /** A world-space offset from the followed node, such as above its head. */
    var followOffset: Vec3f = Vec3f(defaults.followOffset.x, defaults.followOffset.y, defaults.followOffset.z)

    /** Whether the element covers the screen rectangle of the followed node. */
    var followBounds: Boolean = defaults.followBounds

    /** How far a [followBounds] box reaches past the node's rectangle on each side. */
    var followOutset: Dp = Dp(defaults.followOutset)

    /** How a follower behaves with many on screen; see [SceneCanvasElement.followRules]. */
    var followRules: CanvasFollowRules? = defaults.followRules

    /** Whether the element is the arrow of a follower held to the screen's edge. */
    var followArrow: Boolean = defaults.followArrow

    /** How the element places its children in rows or columns; null anchors each by hand. */
    var layout: CanvasLayout? = defaults.layout

    /** In a parent with a [layout], the element's share of its line's leftover space. */
    var grow: Float = defaults.grow

    /** Game state the element shows without code: a Bar's fill, a Text's words, a cooldown. */
    var bind: CanvasBinding? = defaults.bind

    /** Whether the element takes the mouse wheel while the pointer is over it. */
    var wheel: Boolean = defaults.wheel

    /** Whether the element can be picked up and dragged. */
    var draggable: Boolean = defaults.draggable

    /** Whether a dragged element released over this one is reported here. */
    var dropTarget: Boolean = defaults.dropTarget

    /** A View's camera: the name of a node with a camera. */
    var camera: String = defaults.camera

    /** Whether a Text's `[[key|words]]` spans draw as links. */
    var links: Boolean = defaults.links

    /** A TextField's password mask character; empty draws the text as typed. */
    var mask: String = defaults.mask

    /** The font the element's words draw in: a project `.ttf` or `.otf` path, `default` or `bitmap`. */
    var font: String = defaults.font

    /** Whether an Image or a Panel takes a tap as a Button does. */
    var pressable: Boolean = defaults.pressable

    /** The most a TextField lets the player type, in [maxLengthUnit]; 0 for no limit. */
    var maxLength: Int = defaults.maxLength

    /** What [maxLength] counts. */
    var maxLengthUnit: TextLengthUnit = defaults.maxLengthUnit

    /** The key of the hint a TextField shows while it is empty and unfocused. */
    var placeholder: String = defaults.placeholder

    /** The space between a TextField's edge and its text, in dp. */
    var padding: CanvasPadding = defaults.padding

    /** The key of the hint shown near the pointer after it rests on the element. */
    var tooltip: String = defaults.tooltip

    /** How a Joystick moves and what it reports. */
    var joystick: CanvasJoystick? = defaults.joystick

    /** How a Button acts as an on-screen action button: its trigger, long press and cooldown. */
    var button: CanvasButton? = defaults.button

    /** Whether a Button can be pressed. */
    var enabled: Boolean = defaults.enabled

    /** How a LookArea turns a swipe into a look and a pinch into a zoom. */
    var look: CanvasLook? = defaults.look

    /** Places the element's children as a button cluster, in place of [layout]. */
    var cluster: CanvasCluster? = defaults.cluster

    /** How this control looks, slot by slot, over the scene's controls theme. */
    var skin: CanvasControlSkin? = defaults.skin

    /** The name a player's saved layout knows this control by. */
    var controlId: String = defaults.controlId

    /** How a Bar draws as a progress bar: direction, track, trail, segments, ticks and label. */
    var bar: CanvasBar? = defaults.bar

    /** How a Sweep fills its value of a full turn. */
    var sweep: CanvasSweep? = defaults.sweep

    /** A Stepper's number and its range. */
    var stepper: CanvasStepper? = defaults.stepper

    /** Sets [offsetX] and [offsetY]. */
    fun offset(x: Dp, y: Dp) {
        offsetX = x
        offsetY = y
    }

    /** Sets [width] and [height]. */
    fun size(width: Dp, height: Dp) {
        this.width = width
        this.height = height
    }

    /**
     * Adds another component to this node beside its `canvas_element`, such as a `tag` or a
     * `blueprint`.
     */
    fun component(component: SceneComponent) {
        require(component !is SceneCanvasElement) { "$name already has its canvas_element; add a child element instead" }
        components += component
    }

    /** Adds a child element of [kind], named [name], set up by [block]. */
    fun element(name: String, kind: CanvasElementKind, id: String = "", block: CanvasElementScope.() -> Unit = {}) {
        children += CanvasElementScope(name, kind, id).apply(block).build()
    }

    /** Adds a Text child. */
    fun text(name: String, id: String = "", block: CanvasElementScope.() -> Unit = {}) = element(name, CanvasElementKind.Text, id, block)

    /** Adds a Panel child. */
    fun panel(name: String, id: String = "", block: CanvasElementScope.() -> Unit = {}) = element(name, CanvasElementKind.Panel, id, block)

    /** Adds a Bar child: a progress bar, set up by its [bar] settings. */
    fun bar(name: String, id: String = "", block: CanvasElementScope.() -> Unit = {}) = element(name, CanvasElementKind.Bar, id, block)

    /** Adds a Button child: an action button when its [button] settings are set. */
    fun button(name: String, id: String = "", block: CanvasElementScope.() -> Unit = {}) = element(name, CanvasElementKind.Button, id, block)

    /** Adds a TextField child. */
    fun textField(name: String, id: String = "", block: CanvasElementScope.() -> Unit = {}) =
        element(name, CanvasElementKind.TextField, id, block)

    /** Adds a Joystick child. */
    fun joystick(name: String, id: String = "", block: CanvasElementScope.() -> Unit = {}) =
        element(name, CanvasElementKind.Joystick, id, block)

    /** Adds an Image child: its picture is its [style]'s image. */
    fun image(name: String, id: String = "", block: CanvasElementScope.() -> Unit = {}) = element(name, CanvasElementKind.Image, id, block)

    /** Adds a View child: what the [camera] it names sees. */
    fun view(name: String, id: String = "", block: CanvasElementScope.() -> Unit = {}) = element(name, CanvasElementKind.View, id, block)

    /** Adds a LookArea child. */
    fun lookArea(name: String, id: String = "", block: CanvasElementScope.() -> Unit = {}) =
        element(name, CanvasElementKind.LookArea, id, block)

    /** Adds a Sweep child: a radial fill, set up by its [sweep] settings. */
    fun sweep(name: String, id: String = "", block: CanvasElementScope.() -> Unit = {}) = element(name, CanvasElementKind.Sweep, id, block)

    /** Adds a Stepper child. */
    fun stepper(name: String, id: String = "", block: CanvasElementScope.() -> Unit = {}) = element(name, CanvasElementKind.Stepper, id, block)

    internal fun build(): SceneNode = SceneNode(
        name = name,
        components = listOf(toSceneComponent()) + components,
        children = children.toList(),
    )

    private fun toSceneComponent(): SceneCanvasElement = SceneCanvasElement(
        kind = kind,
        anchor = anchor,
        offsetX = offsetX.value,
        offsetY = offsetY.value,
        width = width.value,
        height = height.value,
        text = text,
        fontSize = fontSize.value,
        color = color.toHex(),
        background = background.toHex(),
        value = value,
        order = order,
        visible = visible,
        action = action,
        doubleAction = doubleAction,
        touchOnly = touchOnly,
        safeArea = safeArea,
        style = style,
        textAlign = textAlign,
        follow = follow,
        followOffset = SceneVec3(followOffset.x, followOffset.y, followOffset.z),
        followBounds = followBounds,
        followOutset = followOutset.value,
        followRules = followRules,
        followArrow = followArrow,
        layout = layout,
        grow = grow,
        bind = bind,
        wheel = wheel,
        draggable = draggable,
        dropTarget = dropTarget,
        camera = camera,
        links = links,
        mask = mask,
        font = font,
        pressable = pressable,
        maxLength = maxLength,
        maxLengthUnit = maxLengthUnit,
        placeholder = placeholder,
        padding = padding,
        tooltip = tooltip,
        textKey = textKey,
        textArgs = textArgs.toMap(),
        joystick = joystick,
        button = button,
        enabled = enabled,
        look = look,
        cluster = cluster,
        skin = skin,
        controlId = controlId,
        bar = bar,
        sweep = sweep,
        stepper = stepper,
        elementId = elementId,
    )
}
