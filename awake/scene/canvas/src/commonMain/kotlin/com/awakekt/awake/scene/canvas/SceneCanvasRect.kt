/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.core.text.font.FontNumerals
import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasAppearance
import com.awakekt.awake.ui.canvas.CanvasControlSkin
import com.awakekt.awake.ui.canvas.CanvasRect
import com.awakekt.awake.ui.canvas.CanvasStyle
import com.awakekt.awake.ui.canvas.CanvasText
import com.awakekt.awake.ui.canvas.canvasFontProblem
import com.awakekt.awake.ui.canvas.isCanvasColor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The saved forms of the components every canvas element is placed, worded and drawn by. A node
// with a `canvas_rect` is a canvas element; the others add to it.

/**
 * The saved form of a [CanvasRect]: where a canvas element sits and how big it is. A node that has
 * one is a canvas element.
 *
 * @property anchor The screen point, or the parent's, the element is pinned to.
 * @property offsetX How far, in dp, the element moves from its anchor along the X axis: inward from
 * a left or right edge, and right of centre on a centred axis, or left of it when negative.
 * @property offsetY How far, in dp, the element moves from its anchor along the Y axis: inward from
 * a top or bottom edge, and below centre on a centred axis, or above it when negative.
 * @property width Width in dp.
 * @property height Height in dp.
 * @property order Z-ordering layer for overlapping elements: a higher one draws on top.
 * @property visible Whether the element is drawn.
 * @property touchOnly Whether the element is drawn only while touch controls are shown.
 * @property safeArea Whether an anchored element on the canvas itself keeps inside the safe area.
 * @property grow In a parent with a `canvas_layout`, the element's share of its line's leftover space.
 * @property elementId The stable id code finds the element by, such as `health-bar`; empty for none.
 * Letters, digits, `_`, `.` and `-`, starting with a letter, digit or `_`; unique within one prefab.
 * @property controlId The name a player's saved layout knows this control by, such as `move`; empty for none.
 */
@Serializable
@SerialName("canvas_rect")
@SceneBinding(CanvasRect::class, runtimeOnly = ["screenBounds"])
data class SceneCanvasRect(
    val anchor: CanvasAnchor = CanvasAnchor.TopLeft,
    val offsetX: Float = CanvasRect.DEFAULT_INSET,
    val offsetY: Float = CanvasRect.DEFAULT_INSET,
    @PropertyRange(min = 0.0) val width: Float = CanvasRect.DEFAULT_WIDTH,
    @PropertyRange(min = 0.0) val height: Float = CanvasRect.DEFAULT_HEIGHT,
    val order: Int = 0,
    val visible: Boolean = true,
    val touchOnly: Boolean = false,
    val safeArea: Boolean = true,
    @PropertyRange(min = 0.0) val grow: Float = 0f,
    val elementId: String = "",
    val controlId: String = "",
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = listOfNotNull(
        "size must not be negative".takeIf { width < 0f || height < 0f },
        "grow must not be negative".takeIf { grow < 0f },
        "elementId \"$elementId\" $ELEMENT_ID_RULE".takeIf { elementId.isNotEmpty() && !isCanvasElementId(elementId) },
    ).map { SceneValidationIssue(path, "canvas_rect.$it") }
}

/**
 * The saved form of a [CanvasText]: a canvas element's words. With no widget component beside it,
 * the element is a Text.
 *
 * @property text The words shown while [textKey] is empty: a Text's content, a Button's label, a
 * TextField's starting line.
 * @property textKey The key of the words in the project's string tables, `strings/<locale>.json`,
 * such as `hud.greeting`; empty shows [text].
 * @property textArgs Values for [textKey]'s placeholders, and a TextField's placeholder hint's, by
 * name, such as `"count": "3"`.
 * @property fontSize Font size in sp.
 * @property font A project path ending in `.ttf` or `.otf`, or a built-in family, `default` or
 * `bitmap`; empty draws in the default font.
 * @property textAlign Where the words sit in the element; null keeps the widget's own.
 * @property links Whether `[[key|shown words]]` spans draw as links; false for text a game cannot trust.
 * @property numerals Digit width spacing (`Tabular` or `Proportional`); null keeps the font's default digits.
 */
@Serializable
@SerialName("canvas_text")
@SceneBinding(CanvasText::class)
data class SceneCanvasText(
    val text: String = "",
    val textKey: String = "",
    val textArgs: Map<String, String> = emptyMap(),
    @PropertyRange(min = 0.0, exclusiveMin = true) val fontSize: Float = CanvasText.DEFAULT_FONT_SIZE,
    val font: String = "",
    val textAlign: CanvasAnchor? = null,
    val links: Boolean = true,
    val numerals: FontNumerals? = null,
) : SceneComponent {
    /** [SceneCanvasText] as it was before `numerals`: hidden, and kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        text: String = "",
        textKey: String = "",
        textArgs: Map<String, String> = emptyMap(),
        fontSize: Float = CanvasText.DEFAULT_FONT_SIZE,
        font: String = "",
        textAlign: CanvasAnchor? = null,
        links: Boolean = true,
    ) : this(text, textKey, textArgs, fontSize, font, textAlign, links)

    /** [copy] as it was before `numerals`: hidden, and kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        text: String = this.text,
        textKey: String = this.textKey,
        textArgs: Map<String, String> = this.textArgs,
        fontSize: Float = this.fontSize,
        font: String = this.font,
        textAlign: CanvasAnchor? = this.textAlign,
        links: Boolean = this.links,
    ): SceneCanvasText = copy(text, textKey, textArgs, fontSize, font, textAlign, links)

    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (fontSize <= 0f) add("fontSize must be positive")
        canvasFontProblem(font)?.let(::add)
        if (textKey.isNotEmpty() && textKey.isBlank()) add("textKey must not be blank")
        if (textKey.isNotEmpty() && text.isNotEmpty()) add("textKey and text both set the words; keep one")
    }.map { SceneValidationIssue(path, "canvas_text.$it") }

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> = validate(path) + buildList {
        val bind = peers.firstNotNullOfOrNull { (it as? SceneCanvasBind)?.bind }
        if (textKey.isNotEmpty() && !bind?.text.isNullOrBlank()) add("textKey and canvas_bind.bind.text both set the words; keep one")
        val hinted = peers.any { it is SceneCanvasTextField && it.placeholder.isNotEmpty() }
        val hasArgs = textArgs.isNotEmpty() || !bind?.textArgs.isNullOrEmpty()
        if (textKey.isEmpty() && !hinted && hasArgs) {
            add("textArgs fill in a textKey's or a placeholder's text; set one")
        }
    }.map { SceneValidationIssue(path, "canvas_text.$it") }
}

/**
 * The saved form of a [CanvasAppearance]: how a canvas element looks beyond its words. An element
 * without one draws white content on nothing.
 *
 * @property color Hex colour of the foreground: a Text's words, a Bar's or a Sweep's fill, a Joystick's knob.
 * @property background Hex colour of the element's background.
 * @property style Images, and their frames and fills, borders, corners, shadow and per-state looks.
 * @property skin How a control looks, slot by slot, over the scene's `canvas_controls` theme.
 */
@Serializable
@SerialName("canvas_appearance")
@SceneBinding(CanvasAppearance::class, runtimeOnly = ["previewState"])
data class SceneCanvasAppearance(
    val color: String = CanvasAppearance.DEFAULT_COLOR,
    val background: String = CanvasAppearance.DEFAULT_BACKGROUND,
    val style: CanvasStyle = CanvasStyle(),
    val skin: CanvasControlSkin? = null,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        listOf(color, background).filterNot(::isCanvasColor).forEach { add("colour \"$it\" must be #RRGGBB or #RRGGBBAA") }
        style.problems().forEach { add("style.$it") }
        skin?.problems()?.forEach { add("skin.$it") }
    }.map { SceneValidationIssue(path, "canvas_appearance.$it") }
}
