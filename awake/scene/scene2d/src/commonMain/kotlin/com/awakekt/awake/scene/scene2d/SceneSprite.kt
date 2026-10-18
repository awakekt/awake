/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec2
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// This file is the scene-document schema of `sprite` and nothing else: the data a scene file can hold and
// the checks on it. SpriteBinding attaches it to an entity. Nothing here needs a renderer.

/** Image pixels per scene unit when a sprite does not say: 100 pixels to a unit. */
private const val DEFAULT_PIXELS_PER_UNIT = 100f

/** The middle of a frame, as a fraction of it: where a sprite's pivot is unless it says. */
private const val CENTRE = 0.5f

/**
 * A flat image drawn at the node's `Transform`: one cell of a [columns] x [rows] frame sheet, with no
 * lighting. Its size is the cell's pixel size divided by [pixelsPerUnit], so a sprite keeps its size
 * under an orthographic camera however the image is scaled.
 *
 * @property texture A project image file the sprite shows.
 * @property columns Frame-sheet columns. At least 1.
 * @property rows Frame-sheet rows. At least 1.
 * @property frame The cell shown, counted in reading order from 0 at the top left. Within the sheet.
 * @property pixelsPerUnit Image pixels that make one scene unit. Above 0.
 * @property flipX Mirrors the sprite left to right.
 * @property flipY Mirrors the sprite top to bottom.
 * @property tint Multiplied into the image's colour and alpha.
 * @property sortOrder Within its [sortingLayer], sprites with a higher order draw over those with a
 * lower one; a tie draws by depth from the camera.
 * @property pivot The point of the frame placed at the node's position, which the sprite turns and
 * scales about: a fraction of the frame from its bottom left, so (0.5, 0.5), the default, is its
 * centre and (0.5, 0) the middle of its bottom edge. It holds for every frame of the sheet, and a
 * flipped sprite mirrors about it. A value outside 0 to 1 places it outside the frame.
 * @property sortingLayer The project's sorting layer the sprite draws on, by name: a later layer
 * draws over an earlier one whatever their [sortOrder]. A name the project does not list draws on
 * [SortingLayers.DEFAULT].
 */
@Serializable
@SerialName("sprite")
data class SceneSprite(
    val texture: String,
    @PropertyRange(min = 1.0) val columns: Int = 1,
    @PropertyRange(min = 1.0) val rows: Int = 1,
    val frame: Int = 0,
    @PropertyRange(min = 0.0, exclusiveMin = true) val pixelsPerUnit: Float = DEFAULT_PIXELS_PER_UNIT,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    val tint: SceneColor = SceneColor(),
    val sortOrder: Int = 0,
    val pivot: SceneVec2 = SceneVec2(CENTRE, CENTRE),
    val sortingLayer: String = SortingLayers.DEFAULT,
) : SceneComponent {
    /** Cells in the sheet; 0 when [columns] or [rows] is not valid. */
    val cellCount: Int get() = if (columns >= 1 && rows >= 1 && columns.toLong() * rows <= Int.MAX_VALUE) columns * rows else 0

    override fun validate(path: String): List<SceneValidationIssue> =
        problems().map { SceneValidationIssue(path, "sprite.$it") }

    private fun problems(): List<String> = buildList {
        if (texture.isBlank()) add("texture must name an image")
        if (columns < 1) add("columns must be at least 1")
        if (rows < 1) add("rows must be at least 1")
        if (columns.toLong() * rows > Int.MAX_VALUE) add("sheet cell count must fit in an Int")
        if (frame < 0) {
            add("frame must not be negative")
        } else if (cellCount > 0 && frame >= cellCount) {
            add("frame must stay within the sheet's $cellCount cells: it is $frame")
        }
        if (pixelsPerUnit <= 0f || !pixelsPerUnit.isFinite()) add("pixelsPerUnit must be finite and above 0")
        if (!pivot.x.isFinite() || !pivot.y.isFinite()) add("pivot must be finite")
        if (sortingLayer.isBlank()) add("sortingLayer must name a layer")
    }
}
