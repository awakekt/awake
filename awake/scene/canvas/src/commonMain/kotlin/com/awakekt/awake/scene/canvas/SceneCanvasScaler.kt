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
import com.awakekt.awake.ui.canvas.CanvasScaleMode
import com.awakekt.awake.ui.canvas.CanvasScaleScope
import com.awakekt.awake.ui.canvas.CanvasScaleSnap
import com.awakekt.awake.ui.canvas.CanvasScaler
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The saved form of a [CanvasScaler]: how the scene's canvas, or one subtree of it, works out its
 * scale from the screen's size. With [CanvasScaleScope.Canvas], the default, the scene has one on any
 * node and [SceneCanvas] draws the whole canvas at the scale it gives; with [CanvasScaleScope.Subtree],
 * only the node it sits on and the nodes beneath it draw at that scale, while the rest of the canvas
 * keeps its own.
 *
 * @property mode Which rule works the scale out: `ScaleWithScreenSize`, `ConstantPixelSize` or
 * `ConstantPhysicalSize`.
 * @property referenceWidth With `ScaleWithScreenSize`, the width the canvas is designed at, in units.
 * @property referenceHeight With `ScaleWithScreenSize`, the height the canvas is designed at, in
 * units.
 * @property match With `ScaleWithScreenSize`, what the scale follows: 0 the screen's width, 1 its
 * height, and a log-scale blend between.
 * @property pixelsPerUnit With `ConstantPixelSize`, how many of the screen's pixels one unit covers.
 * @property referenceDpi With `ConstantPhysicalSize`, the dots per inch the canvas is designed at.
 * @property minScale The smallest scale the rule may give; 0 for no limit.
 * @property maxScale The largest scale the rule may give; 0 for no limit.
 * @property playerScale The player's own multiplier, applied after the limits.
 * @property snap Rounds the scale the rule gives to a step before the limits hold it: `None`, the
 * default, `WholeStep` or `HalfStep`. Layout then scales by the rounded factor.
 * @property scope Which part of the canvas the scaler applies to: `Canvas`, the default, for the
 * whole canvas, or `Subtree` for only this node and the nodes beneath it.
 */
@Serializable
@SerialName("canvas_scaler")
@SceneBinding(CanvasScaler::class)
data class SceneCanvasScaler(
    val mode: CanvasScaleMode = CanvasScaleMode.ScaleWithScreenSize,
    @PropertyRange(min = 0.0, exclusiveMin = true) val referenceWidth: Float = CanvasScaler.DEFAULT_REFERENCE_WIDTH,
    @PropertyRange(min = 0.0, exclusiveMin = true) val referenceHeight: Float = CanvasScaler.DEFAULT_REFERENCE_HEIGHT,
    @PropertyRange(min = 0.0, max = 1.0) val match: Float = CanvasScaler.DEFAULT_MATCH,
    @PropertyRange(min = 0.0, exclusiveMin = true) val pixelsPerUnit: Float = 1f,
    @PropertyRange(min = 0.0, exclusiveMin = true) val referenceDpi: Float = CanvasScaler.DP_PER_INCH,
    @PropertyRange(min = 0.0) val minScale: Float = 0f,
    @PropertyRange(min = 0.0) val maxScale: Float = 0f,
    @PropertyRange(min = 0.0, exclusiveMin = true) val playerScale: Float = 1f,
    val snap: CanvasScaleSnap = CanvasScaleSnap.None,
    val scope: CanvasScaleScope = CanvasScaleScope.Canvas,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> =
        CanvasScalerBinding.run { toComponent() }.problems().map { SceneValidationIssue(path, "canvas_scaler.$it") }
}

/** The scene's whole-canvas [CanvasScaler]: the first one with [CanvasScaleScope.Canvas] on any node, or null when it has none. */
fun World.canvasScaler(): CanvasScaler? {
    family<CanvasScaler>().forEachComponent { if (it.scope == CanvasScaleScope.Canvas) return it }
    return null
}
