/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.CanvasCluster
import com.awakekt.awake.ui.canvas.CanvasFollow
import com.awakekt.awake.ui.canvas.CanvasFollowArrow
import com.awakekt.awake.ui.canvas.CanvasFollowRules
import com.awakekt.awake.ui.canvas.CanvasInput
import com.awakekt.awake.ui.canvas.CanvasLayout
import com.awakekt.awake.ui.canvas.CanvasTooltip
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The saved forms of the components that place an element's children, bind it to game state,
// make it follow a node, and set how it takes the pointer.

/**
 * The saved form of a [CanvasLayout]: how a canvas element places its children itself, in rows or
 * columns, with gap, padding, wrap and alignment.
 *
 * @property layout The layout's settings.
 */
@Serializable
@SerialName("canvas_layout")
@SceneBinding(CanvasLayout::class, whole = "layout")
data class SceneCanvasLayout(val layout: CanvasLayout = CanvasLayout()) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> =
        layout.problems().map { SceneValidationIssue(path, "canvas_layout.layout.$it") }

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> = validate(path) + listOfNotNull(
        "canvas_cluster and canvas_layout both place the children; keep one".takeIf { peers.any { it is SceneCanvasCluster } },
    ).map { SceneValidationIssue(path, "canvas_layout.$it") }
}

/**
 * The saved form of a [CanvasCluster]: a canvas element's children placed as a button cluster, the
 * first on a pivot and the rest on an arc around it.
 *
 * @property cluster The cluster's settings.
 */
@Serializable
@SerialName("canvas_cluster")
@SceneBinding(CanvasCluster::class, whole = "cluster")
data class SceneCanvasCluster(val cluster: CanvasCluster = CanvasCluster()) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> =
        cluster.problems().map { SceneValidationIssue(path, "canvas_cluster.cluster.$it") }
}

/**
 * The saved form of a [CanvasBinding]: game state a canvas element shows without code, such as a
 * Bar's fill, a Text's words or a Button's cooldown, read from a node's fields.
 *
 * @property bind The node and the fields read.
 */
@Serializable
@SerialName("canvas_bind")
@SceneBinding(CanvasBinding::class, name = "CanvasBindBinding", whole = "bind")
data class SceneCanvasBind(val bind: CanvasBinding) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = bind.problems().map { SceneValidationIssue(path, "canvas_bind.bind.$it") }

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> = validate(path) + buildList {
        val cooldownSeconds = peers.firstNotNullOfOrNull { (it as? SceneCanvasButton)?.settings?.cooldownSeconds } ?: 0f
        if (cooldownSeconds > 0f && bind.cooldown.isNotBlank()) add("canvas_button.settings.cooldownSeconds and bind.cooldown both set the cooldown; keep one")
    }.map { SceneValidationIssue(path, "canvas_bind.$it") }
}

/**
 * The saved form of a [CanvasFollow]: a canvas element that follows a node on screen.
 *
 * @property node The name of the node followed.
 * @property offset A world-space offset from the node, such as above its head.
 * @property bounds Whether the element covers the screen rectangle of the node's meshes instead of
 * standing at its point.
 * @property outset How far, in dp, a [bounds] box reaches past the node's rectangle on each side;
 * negative pulls it in.
 * @property rules How the follower behaves with many on screen: culling, ranking, scale and fade by
 * distance, and holding to the screen's edge. Null culls it off screen and hides it behind the camera.
 */
@Serializable
@SerialName("canvas_follow")
@SceneBinding(CanvasFollow::class, converters = [CanvasVec3Converter::class], runtimeOnly = ["score", "pinned", "pinAngle", "distance"])
data class SceneCanvasFollow(
    val node: String = "",
    val offset: SceneVec3 = SceneVec3(),
    val bounds: Boolean = false,
    val outset: Float = 0f,
    val rules: CanvasFollowRules? = null,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (node.isBlank()) add("node must name the node followed")
        rules?.problems()?.forEach { add("rules.$it") }
    }.map { SceneValidationIssue(path, "canvas_follow.$it") }

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> = validate(path) + buildList {
        if (peers.any { it is SceneCanvasFollowArrow }) add("a canvas_follow_arrow points for an element inside a follower, so it cannot follow a node itself")
    }.map { SceneValidationIssue(path, "canvas_follow.$it") }
}

/**
 * The saved form of a [CanvasFollowArrow]: this element, inside a follower, is the arrow of one held
 * to the screen's edge, drawn only while its parent is pinned and turned to point at the target.
 */
@Serializable
@SerialName("canvas_follow_arrow")
@SceneBinding(CanvasFollowArrow::class)
class SceneCanvasFollowArrow : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    /** Every arrow is the same: there is nothing to set. */
    override fun equals(other: Any?): Boolean = other is SceneCanvasFollowArrow

    override fun hashCode(): Int = SceneCanvasFollowArrow::class.hashCode()

    override fun toString(): String = "SceneCanvasFollowArrow"
}

/**
 * The saved form of a [CanvasInput]: how a canvas element takes the pointer beyond what its widget does.
 *
 * @property pressable Whether an Image or a Panel takes a tap as a Button does.
 * @property draggable Whether the element can be picked up and dragged.
 * @property dropTarget Whether a dragged element released over this one is reported here.
 * @property wheel Whether the element takes the mouse wheel while the pointer is over it.
 * @property moveHandle Whether dragging the element moves its window, its nearest root element: a
 * window's title bar.
 * @property moveStrip How much of a [moveHandle]'s window, in the window's dp, stays on the canvas;
 * 0 keeps its top down to the handle's bottom.
 */
@Serializable
@SerialName("canvas_input")
@SceneBinding(CanvasInput::class)
data class SceneCanvasInput(
    val pressable: Boolean = false,
    val draggable: Boolean = false,
    val dropTarget: Boolean = false,
    val wheel: Boolean = false,
    val moveHandle: Boolean = false,
    @PropertyRange(min = 0.0) val moveStrip: Float = 0f,
) : SceneComponent {
    /** [SceneCanvasInput] as it was before `moveHandle`: hidden, and kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        pressable: Boolean = false,
        draggable: Boolean = false,
        dropTarget: Boolean = false,
        wheel: Boolean = false,
    ) : this(pressable, draggable, dropTarget, wheel, false, 0f)

    /** [copy] as it was before `moveHandle`: hidden, and kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        pressable: Boolean = this.pressable,
        draggable: Boolean = this.draggable,
        dropTarget: Boolean = this.dropTarget,
        wheel: Boolean = this.wheel,
    ): SceneCanvasInput = copy(pressable, draggable, dropTarget, wheel, moveHandle, moveStrip)

    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> = listOfNotNull(
        "pressable makes an Image or a Panel pressable; a Button always is".takeIf {
            pressable && peers.any { it.isCanvasWidget() && it !is SceneCanvasImage }
        },
        "moveHandle and draggable both drag the element; keep one".takeIf { moveHandle && draggable },
        "moveStrip must not be negative".takeIf { moveStrip < 0f },
        "moveStrip only sets how much of a moveHandle's window stays on screen".takeIf { moveStrip != 0f && !moveHandle },
    ).map { SceneValidationIssue(path, "canvas_input.$it") }
}

/**
 * The saved form of a [CanvasTooltip]: a short hint shown near the pointer after it rests on the element.
 *
 * @property text The key of the hint in the project's string tables, such as `hud.inventory.tooltip`.
 * @property textArgs Values for the hint's placeholders when the element has no `canvas_text`; one
 * that has fills them from its own `textArgs`.
 */
@Serializable
@SerialName("canvas_tooltip")
@SceneBinding(CanvasTooltip::class)
data class SceneCanvasTooltip(
    val text: String = "",
    val textArgs: Map<String, String> = emptyMap(),
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = listOfNotNull(
        "text must name the hint's key".takeIf { text.isBlank() },
    ).map { SceneValidationIssue(path, "canvas_tooltip.$it") }
}
