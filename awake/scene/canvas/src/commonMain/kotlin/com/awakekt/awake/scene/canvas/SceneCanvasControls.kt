/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.ui.canvas.CanvasControlTheme
import com.awakekt.awake.ui.canvas.CanvasControls
import com.awakekt.awake.ui.canvas.CanvasPlayerLayout
import com.awakekt.awake.ui.canvas.CanvasTouchVisibility
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The saved form of [CanvasControls]: what every on-screen control on the scene's canvas shares. A
 * scene has one, on any node; without one, touch controls show as the host says.
 *
 * @property touchVisibility When `touchOnly` elements show: `Host`, as the host says; `Auto`, after a
 * touch, and hidden after a controller, a key or the mouse, switching live; `Always`; or `Never`.
 * @property minTouchSize The smallest a stick, action button or look area is to a thumb, in dp; one
 * drawn smaller takes a touch up to this size around its centre.
 * @property theme How every control looks, slot by slot: a `preset` (`MinimalFlat`, `Glass`,
 * `Textured` or `None`), `colors` and `images` tokens a slot names as `@name`, and a `skin` over the
 * preset's. Null draws each control plainly from its own colours.
 * @property playerLayout The layout the scene starts with, before a player's own replaces it: every
 * touch control's `scale` and `opacity`, and each control's own `offsetX`, `offsetY`, `scale` and
 * `opacity` by its `controlId`.
 */
@Serializable
@SerialName("canvas_controls")
@SceneBinding(CanvasControls::class)
data class SceneCanvasControls(
    val touchVisibility: CanvasTouchVisibility = CanvasTouchVisibility.Host,
    @PropertyRange(min = 0.0) val minTouchSize: Float = CanvasControls.DEFAULT_MIN_TOUCH_SIZE,
    val theme: CanvasControlTheme? = null,
    val playerLayout: CanvasPlayerLayout = CanvasPlayerLayout(),
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> =
        CanvasControlsBinding.run { toComponent() }.problems().map { SceneValidationIssue(path, "canvas_controls.$it") }
}

/** The scene's [CanvasControls]: the first on any node, or null when it has none. */
fun World.canvasControls(): CanvasControls? {
    family<CanvasControls>().forEachComponent { return it }
    return null
}
