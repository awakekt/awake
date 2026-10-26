/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.core.text.unicode.TextLengthUnit
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasBar
import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.CanvasButton
import com.awakekt.awake.ui.canvas.CanvasCluster
import com.awakekt.awake.ui.canvas.CanvasControlSkin
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasFollowRules
import com.awakekt.awake.ui.canvas.CanvasJoystick
import com.awakekt.awake.ui.canvas.CanvasLayout
import com.awakekt.awake.ui.canvas.CanvasLook
import com.awakekt.awake.ui.canvas.CanvasPadding
import com.awakekt.awake.ui.canvas.CanvasStepper
import com.awakekt.awake.ui.canvas.CanvasStyle
import com.awakekt.awake.ui.canvas.CanvasSweep
import com.awakekt.awake.ui.canvas.canvasFontProblem
import com.awakekt.awake.ui.canvas.isCanvasColor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The saved schema form of a [CanvasElement].
 *
 * @property kind The rendering kind and semantic type of this element.
 * @property anchor Screen anchor point determining reference origin.
 * @property offsetX How far, in dp, the element moves from its anchor along the X axis: inward from
 * a left or right edge, where a negative offset is held at the edge, and right of centre on a
 * centred axis, or left of it when negative.
 * @property offsetY How far, in dp, the element moves from its anchor along the Y axis: inward from
 * a top or bottom edge, where a negative offset is held at the edge, and below centre on a centred
 * axis, or above it when negative.
 * @property width Width in density-independent pixels.
 * @property height Height in density-independent pixels.
 * @property text Textual content for text displays and button labels.
 * @property fontSize Font size in scale-independent pixels.
 * @property color Hex color code for foreground content or knob.
 * @property background Hex color code for element background.
 * @property value Normalized fill value for progress bars (0.0 to 1.0).
 * @property order Z-ordering layer index for overlapping elements.
 * @property visible Whether this element is rendered.
 * @property action Action trigger identifier associated with button activation.
 * @property doubleAction What a Button does when double-clicked: two presses within
 * [CanvasElement.DOUBLE_CLICK_SECONDS]. The second press still fires [action] first.
 * @property touchOnly If true, element is displayed only when touch controls are active.
 * @property safeArea Whether an anchored element on the canvas itself keeps inside the safe area,
 * clear of a notch, rounded corners or a home indicator; false measures its anchor and offsets from
 * the screen's own edges, for a backdrop that reaches under a notch. An element inside another, or
 * one that follows a node, is placed the same either way.
 * @property style How the element looks beyond its colours: images, and their frames and fills.
 * @property textAlign Where a Text's or Button's text sits in the element; null keeps the kind's own,
 * top-left for a Text and centred for a Button.
 * @property follow The name of a node the element follows on screen; empty follows none.
 * @property followOffset A world-space offset from the followed node, such as above its head.
 * @property followBounds Whether the element covers the screen rectangle of the followed node's
 * meshes instead of standing at its point.
 * @property followOutset How far, in dp, a [followBounds] box reaches past the node's rectangle on
 * each side, so children anchored to its corners sit outside the node, as a target marker's corner
 * pieces do. Negative pulls the box in; it never shrinks below nothing.
 * @property followRules How a follower behaves with many on screen: whether it is culled off screen
 * (`cullOffScreen`), beyond `maxDistance` or while `occlusion` says it is hidden; the `group` a
 * `canvas_follow_limit` ranks it in and its `priority` there; its `scale` and `fade` by distance, as
 * curves of `points` (`at` a distance, `value` there); and whether it holds to the screen's edge,
 * `edgeMargin` in, while its target is off screen or behind the camera (`pinToEdge`). Null culls it
 * off screen and hides it behind the camera.
 * @property followArrow Whether this element, inside a follower, is the arrow of one held to the
 * screen's edge: drawn only while its parent is pinned, on its parent's edge toward the target, and
 * turned to point there, its picture pointing right before it turns.
 * @property layout How the element places its children itself, in rows or columns; null anchors
 * each child by hand.
 * @property grow In a parent with a [layout], this element's share of its line's leftover space.
 * @property bind Game state the element shows without code: a Bar's fill, a Text's words.
 * @property wheel Whether this element takes the mouse wheel while the pointer is over it or its
 * children, instead of leaving it for gameplay. [CanvasElement.consumeWheel] reads what it took.
 * @property draggable Whether this element can be picked up and dragged: a ghost of it follows the
 * pointer while [CanvasElement.dragging] is true. Releasing on a [dropTarget] reports it there
 * through [CanvasElement.dropped]; releasing elsewhere reports it on this element instead, through
 * [CanvasElement.droppedOutside]. A plain click still fires if the pointer never moves far enough
 * to start a drag.
 * @property dropTarget Whether a [draggable] element released over this one is reported here,
 * through [CanvasElement.dropped].
 * @property camera A View's camera: the name of a node with a camera, whose picture it shows; empty
 * shows none.
 * @property links Whether a Text element's `[[key|shown words]]` spans draw as links; false draws
 * them as typed and reports no key from a click, for text a game cannot trust such as player chat.
 * @property mask A TextField's password mask: one character drawn for each typed one, such as `*`;
 * empty draws the text as typed.
 * @property font The font a Text's, TextField's or Button's words draw in: a project path ending in
 * `.ttf` or `.otf`, any case, loaded with the scene, or a built-in family, `default` (Roboto) or
 * `bitmap` (a fixed-pitch pixel font). Empty draws in the default font, as a file that does not load
 * does.
 * @property pressable Whether an Image or Panel takes a tap as a Button does: reported through
 * [CanvasElement.consumePress] and [CanvasElement.consumePressAt], and kept from what is under it.
 * False leaves it watching the pointer only. A Button is always pressable.
 * @property maxLength The most a TextField lets the player type, counted in [maxLengthUnit]; 0 for
 * no limit. Input stops at the limit and never inside a character, so an emoji goes in whole or not
 * at all.
 * @property maxLengthUnit What [maxLength] counts: `Graphemes`, the characters a player sees, or
 * `CodePoints` or `Utf8Bytes` to match a server's limit.
 * @property textKey The key of a Text's or Button's words in the project's string tables,
 * `strings/<locale>.json`, such as `hud.greeting`; empty shows [text]. The words are the key's text
 * in the active locale, and change when it does. A key no table has shows as the key and is logged
 * once.
 * @property textArgs Values for [textKey]'s placeholders, by name, such as `"count": "3"`. A value
 * the game sets at runtime comes from a field instead, through [bind]'s
 * [textArgs][CanvasBinding.textArgs], and takes the place of the one here.
 * @property joystick How a Joystick moves and what it reports: `mode` (`Fixed`, `Floating` where the
 * thumb lands, or `Dynamic`, dragged along past its reach), `radius`, `restX` and `restY`,
 * `deadZone`, `response` or a `curve`, `directions` (`Analog`, `Four` or `Eight`), `snapBack`, a
 * `sprintThreshold` past which `sprintAction` is held, and `idleAlpha`, `idleDelay` and
 * `fadeSeconds` for fading while untouched. Null is a fixed pad with no dead zone.
 * @property button How a Button acts as an on-screen action button: its `trigger` (`Tap` on
 * release, or `Down`), `longPressSeconds` and the `longPressAction` a long press presses, the
 * `releaseAction` a release presses, and whether it is held back while cooling down
 * (`blockWhileCooling`). Its cooldown runs itself over `cooldownSeconds` each time it fires, or comes
 * from code or [bind], as its charges do. Null is a plain button.
 * @property enabled Whether a Button can be pressed; a disabled one takes no press and draws in its
 * style's `disabled` look. [bind]'s `enabled` field sets it each frame instead.
 * @property look How a LookArea turns a swipe into a look and a pinch into a zoom: `sensitivity`,
 * `invertX`, `invertY`, `pinchZoom`, `zoomSensitivity`, whether it `turnsCamera` in a played project,
 * and the axis `zoomAction` its pinch is pushed to. Null uses the defaults.
 * @property cluster Places the element's children as a button cluster, in place of [layout]: the
 * first on a pivot (`pivotX`, `pivotY`, shares of the element) unless `primaryAtPivot` is false, and
 * the rest `radius` dp from it, from `startAngle` and `spacing` degrees apart, clockwise from the right.
 * @property skin How this control looks, slot by slot, over the scene's `canvas_controls` theme: a
 * Joystick's `base`, `knob`, `sprintRing` and `arrows`; an action button's `background`, `icon`,
 * `label`, `cooldown`, `charges`, `aimCursor`, `aimRange` and `cancelZone`; a LookArea's `feedback`.
 * Each slot has a `style`, `size`, `circle`, `frames`, `custom` content, per-state `states` and
 * `transitionSeconds`; a colour or image path written `@name` is the theme's token.
 * @property controlId The name a player's saved layout knows this control by, such as `move`; empty
 * for none.
 * @property bar How a Bar draws as a progress bar: its `direction` (`LeftToRight`, `RightToLeft`,
 * `BottomToTop`, `TopToBottom`, or `Radial` around its centre, a ring `thickness` dp wide or a pie),
 * a `track` colour, a `trailColor` fill that trails a drop by `trailDelay` seconds and drains at
 * `trailSpeed` a second, `segments` blocks `segmentGap` dp apart, `ticks` in `tickColor`, and whether
 * it shows a `label`, its text or else its value as a percentage. Null fills across from the left.
 * @property sweep How a Sweep fills its value of a full turn: from `startAngle` degrees clockwise from
 * 12 o'clock, `clockwise` or not, cut to a `mask` (`Box`, rounded by the style's `cornerRadius`,
 * `Circle`, `Pill` or `CutCorner`), as a ring `thickness` dp wide or a pie, over a `track` colour.
 * Null is a pie clockwise from 12 o'clock, cut to the box.
 * @property stepper A Stepper's number: its starting `value`, `min`, `max`, `step`, the `decimals` it
 * shows, and the `repeatDelay` and `repeatInterval` of a held button, in seconds. [bind]'s `value`
 * field sets the number each frame instead. Null steps 0 to 100 in ones.
 * @property elementId The stable id code finds this element by, such as `health-bar`; empty for none.
 * It survives a rename of the node and repeats in every instance of a prefab, so code finds it under
 * one instance's root with `World.findCanvasElement`, or in a prefab with `SceneNode.findCanvasNode`.
 * Letters, digits, `_`, `.` and `-`, starting with a letter, digit or `_`; unique within one prefab.
 * @property placeholder The key of the hint a TextField shows while it is empty and does not hold the
 * keyboard, such as `chat.hint`, in the project's string tables; empty shows none. It draws in the
 * field's text colour at half strength. [textArgs] fill in its placeholders, as a [textKey]'s.
 * @property padding The space between a TextField's edge and its text, in dp: `horizontal` (12, shadcn's
 * input) and `vertical` (0). The field clips its text at its edge, so the padding is also the room
 * the overhang of a glyph keeps, such as a leading `j`'s tail.
 * @property tooltip The key of a short hint shown near the pointer after it has been over the element
 * for half a second, such as `hud.inventory.tooltip`; empty shows none. Any kind takes one. It stays
 * inside the canvas and goes when the pointer leaves or presses the element. [textArgs] fill in its
 * placeholders.
 */
@Serializable
@SerialName("canvas_element")
data class SceneCanvasElement(
    val kind: CanvasElementKind = CanvasElementKind.Text,
    val anchor: CanvasAnchor = CanvasAnchor.TopLeft,
    val offsetX: Float = CanvasElement.DEFAULT_INSET,
    val offsetY: Float = CanvasElement.DEFAULT_INSET,
    @PropertyRange(min = 0.0) val width: Float = CanvasElement.DEFAULT_WIDTH,
    @PropertyRange(min = 0.0) val height: Float = CanvasElement.DEFAULT_HEIGHT,
    val text: String = "",
    @PropertyRange(min = 0.0, exclusiveMin = true) val fontSize: Float = CanvasElement.DEFAULT_FONT_SIZE,
    val color: String = "#FFFFFF",
    val background: String = "#00000000",
    @PropertyRange(min = 0.0, max = 1.0) val value: Float = 1f,
    val order: Int = 0,
    val visible: Boolean = true,
    val action: String = "",
    val doubleAction: String = "",
    val touchOnly: Boolean = false,
    val safeArea: Boolean = true,
    val style: CanvasStyle = CanvasStyle(),
    val textAlign: CanvasAnchor? = null,
    val follow: String = "",
    val followOffset: SceneVec3 = SceneVec3(),
    val followBounds: Boolean = false,
    val followOutset: Float = 0f,
    val followRules: CanvasFollowRules? = null,
    val followArrow: Boolean = false,
    val layout: CanvasLayout? = null,
    @PropertyRange(min = 0.0) val grow: Float = 0f,
    val bind: CanvasBinding? = null,
    val wheel: Boolean = false,
    val draggable: Boolean = false,
    val dropTarget: Boolean = false,
    val camera: String = "",
    val links: Boolean = true,
    val mask: String = "",
    val font: String = "",
    val pressable: Boolean = false,
    @PropertyRange(min = 0.0) val maxLength: Int = 0,
    val maxLengthUnit: TextLengthUnit = TextLengthUnit.Graphemes,
    val textKey: String = "",
    val textArgs: Map<String, String> = emptyMap(),
    val joystick: CanvasJoystick? = null,
    val button: CanvasButton? = null,
    val enabled: Boolean = true,
    val look: CanvasLook? = null,
    val cluster: CanvasCluster? = null,
    val skin: CanvasControlSkin? = null,
    val controlId: String = "",
    val bar: CanvasBar? = null,
    val sweep: CanvasSweep? = null,
    val stepper: CanvasStepper? = null,
    val elementId: String = "",
    val placeholder: String = "",
    val padding: CanvasPadding = CanvasPadding(),
    val tooltip: String = "",
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    /**
     * Validates element dimensions, font sizes, values, and hex color syntax.
     *
     * @param path The JSON/document tree path to this component.
     * @return List of validation issues discovered, or empty list if valid.
     */
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (width < 0f || height < 0f) add(SceneValidationIssue(path, "canvas_element size must not be negative"))
        if (fontSize <= 0f) add(SceneValidationIssue(path, "canvas_element.fontSize must be positive"))
        if (value !in 0f..1f) add(SceneValidationIssue(path, "canvas_element.value must be between 0 and 1"))
        listOf(color, background).filterNot(::isCanvasColor).forEach {
            add(SceneValidationIssue(path, "canvas_element colour \"$it\" must be #RRGGBB or #RRGGBBAA"))
        }
        style.problems().forEach { add(SceneValidationIssue(path, "canvas_element.style.$it")) }
        layout?.problems()?.forEach { add(SceneValidationIssue(path, "canvas_element.layout.$it")) }
        if (grow < 0f) add(SceneValidationIssue(path, "canvas_element.grow must not be negative"))
        bind?.problems()?.forEach { add(SceneValidationIssue(path, "canvas_element.bind.$it")) }
        if (mask.length > 1) add(SceneValidationIssue(path, "canvas_element.mask must be one character"))
        if (maxLength < 0) add(SceneValidationIssue(path, "canvas_element.maxLength must not be negative"))
        canvasFontProblem(font)?.let { add(SceneValidationIssue(path, "canvas_element.$it")) }
        kindProblems().forEach { add(SceneValidationIssue(path, "canvas_element.$it")) }
    }

    /** Fields set where they do nothing, and what is wrong with the follow rules. */
    private fun kindProblems(): List<String> = followProblems() + controlProblems() + idProblems() + hintProblems() + listOfNotNull(
        "mask only masks a TextField".takeIf { mask.isNotEmpty() && kind != CanvasElementKind.TextField },
        "maxLength only limits a TextField".takeIf {
            (maxLength != 0 || maxLengthUnit != TextLengthUnit.Graphemes) && kind != CanvasElementKind.TextField
        },
        "pressable makes an Image or a Panel pressable; a Button always is".takeIf {
            pressable && kind != CanvasElementKind.Image && kind != CanvasElementKind.Panel
        },
        "textKey only sets a Text's or a Button's words, or a Bar's label".takeIf {
            textKey.isNotEmpty() && kind != CanvasElementKind.Text && kind != CanvasElementKind.Button && kind != CanvasElementKind.Bar
        },
        "textKey and text both set the words; keep one".takeIf { textKey.isNotEmpty() && text.isNotEmpty() },
        "textKey and bind.text both set the words; keep one".takeIf { textKey.isNotEmpty() && !bind?.text.isNullOrBlank() },
        "textKey must not be blank".takeIf { textKey.isNotEmpty() && textKey.isBlank() },
    )

    /** What is wrong with a TextField's placeholder and padding, any element's tooltip, and the arguments of a key. */
    private fun hintProblems(): List<String> = listOfNotNull(
        "placeholder only hints a TextField's empty field".takeIf { placeholder.isNotEmpty() && kind != CanvasElementKind.TextField },
        "placeholder must not be blank".takeIf { placeholder.isNotEmpty() && placeholder.isBlank() },
        "padding only pads a TextField".takeIf { padding != CanvasPadding() && kind != CanvasElementKind.TextField },
        "textArgs fill in a textKey's, placeholder's or tooltip's text; set one".takeIf {
            textKey.isEmpty() && placeholder.isEmpty() && tooltip.isEmpty() && (textArgs.isNotEmpty() || !bind?.textArgs.isNullOrEmpty())
        },
        "tooltip must not be blank".takeIf { tooltip.isNotEmpty() && tooltip.isBlank() },
    ) + padding.problems().map { "padding.$it" }

    /** What is wrong with the element's id. */
    private fun idProblems(): List<String> = listOfNotNull(
        "elementId \"$elementId\" $ELEMENT_ID_RULE".takeIf { elementId.isNotEmpty() && !isCanvasElementId(elementId) },
    )

    /** What is wrong with a control's settings, and settings put on a kind they do not set up. */
    private fun controlProblems(): List<String> = listOfNotNull(
        "joystick only sets up a Joystick".takeIf { joystick != null && kind != CanvasElementKind.Joystick },
        "button only sets up a Button".takeIf { button != null && kind != CanvasElementKind.Button },
        "button.cooldownSeconds and bind.cooldown both set the cooldown; keep one".takeIf {
            (button?.cooldownSeconds ?: 0f) > 0f && !bind?.cooldown.isNullOrBlank()
        },
        "look only sets up a LookArea".takeIf { look != null && kind != CanvasElementKind.LookArea },
        "cluster and layout both place the children; keep one".takeIf { cluster != null && layout != null },
        "bar only sets up a Bar".takeIf { bar != null && kind != CanvasElementKind.Bar },
        "sweep only sets up a Sweep".takeIf { sweep != null && kind != CanvasElementKind.Sweep },
        "stepper only sets up a Stepper".takeIf { stepper != null && kind != CanvasElementKind.Stepper },
    ) + joystick?.problems().orEmpty().map { "joystick.$it" } + button?.problems().orEmpty().map { "button.$it" } +
        look?.problems().orEmpty().map { "look.$it" } + cluster?.problems().orEmpty().map { "cluster.$it" } +
        skin?.problems().orEmpty().map { "skin.$it" } + bar?.problems().orEmpty().map { "bar.$it" } +
        sweep?.problems().orEmpty().map { "sweep.$it" } + stepper?.problems().orEmpty().map { "stepper.$it" }

    /** What is wrong with the follow rules, and follow fields set where they do nothing. */
    private fun followProblems(): List<String> = buildList {
        followRules?.problems()?.forEach { add("followRules.$it") }
        if (followRules != null && follow.isEmpty()) add("followRules only apply to an element that follows a node")
        if (followArrow && follow.isNotEmpty()) add("followArrow only points for an element inside a follower, so it cannot follow a node itself")
    }
}
