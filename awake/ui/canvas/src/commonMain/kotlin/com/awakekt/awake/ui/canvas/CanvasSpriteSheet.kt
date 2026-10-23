/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.color.Color

/**
 * Uniform grid sprite-sheet helper for slicing cell regions out of a texture atlas [path].
 *
 * When [columns] is greater than `0`, cells wrap into rows of [columns] cells each (`col = cell % columns`,
 * `row = cell / columns`); when [columns] is `0` or negative, cells are laid out in a single horizontal
 * strip (`col = cell`, `row = 0`).
 *
 * @property path Project-relative path to the sprite-sheet image.
 * @property columns Number of cells per row, or `0` for an unbounded single-row horizontal strip.
 * @property cellWidth Width of each cell in pixels.
 * @property cellHeight Height of each cell in pixels; defaults to [cellWidth] for square cells.
 * @property pixelated Whether nearest-neighbor pixel filtering should be used when scaling.
 */
class CanvasSpriteSheet(
    val path: String,
    val columns: Int,
    val cellWidth: Float,
    val cellHeight: Float = cellWidth,
    val pixelated: Boolean = false,
) {
    init {
        require(path.isNotBlank()) { "CanvasSpriteSheet path must not be blank." }
        require(cellWidth > 0f && cellHeight > 0f) {
            "CanvasSpriteSheet cell dimensions must be positive: ${cellWidth}x$cellHeight."
        }
    }

    /**
     * Returns a [CanvasImage] cropped to the zero-based [cell] index with the given [tint] color.
     *
     * @param cell Zero-based cell index in the sprite sheet.
     * @param tint Multiplicative RGBA tint color; defaults to [Color.White].
     * @return The [CanvasImage] for [cell].
     */
    fun image(cell: Int, tint: Color = Color.White): CanvasImage =
        image(cell, tint.toCanvasHexString())

    /**
     * Returns a [CanvasImage] cropped to the zero-based [cell] index with a `#RRGGBB` or `#RRGGBBAA` [tint].
     *
     * @param cell Zero-based cell index in the sprite sheet.
     * @param tint Hex color string (`#RRGGBB` or `#RRGGBBAA`).
     * @return The [CanvasImage] for [cell].
     */
    fun image(cell: Int, tint: String): CanvasImage {
        require(cell >= 0) { "Sprite sheet cell index must be non-negative: $cell." }
        val w = cellWidth.toInt()
        val h = cellHeight.toInt()
        val col = if (columns > 0) cell % columns else cell
        val row = if (columns > 0) cell / columns else 0
        return CanvasImage(
            path = path,
            regionX = col * w,
            regionY = row * h,
            regionWidth = w,
            regionHeight = h,
            tint = tint,
            pixelated = pixelated,
        )
    }

    /**
     * Returns an untinted [CanvasImage] cropped to the zero-based [cell] index.
     *
     * @param cell Zero-based cell index in the sprite sheet.
     * @return The [CanvasImage] for [cell].
     */
    fun region(cell: Int): CanvasImage = image(cell, "#FFFFFF")
}

/**
 * Updates this element's [CanvasElement.enabled] flag and [CanvasElement.style] for a 4-frame
 * horizontal button sprite strip (frame 0 = hovered, 1 = rest, 2 = pressed, 3 = disabled).
 *
 * Frame width is taken from the current `style.image.regionWidth` when positive, or `width.toInt()`
 * otherwise. Does not reallocate [CanvasElement.style] when the frames already match.
 *
 * @param enabled Whether the button is enabled and interactive.
 */
fun CanvasElement.applyFourStateHorizontalStrip(enabled: Boolean) {
    this.enabled = enabled
    val restFrame = if (enabled) FOUR_STATE_REST_FRAME else FOUR_STATE_DISABLED_FRAME
    val hoveredFrame = if (enabled) FOUR_STATE_HOVERED_FRAME else restFrame
    val pressedFrame = if (enabled) FOUR_STATE_PRESSED_FRAME else restFrame
    applyHorizontalStripFrames(
        restFrame = restFrame,
        hoveredFrame = hoveredFrame,
        pressedFrame = pressedFrame,
        disabledFrame = FOUR_STATE_DISABLED_FRAME,
    )
}

/**
 * Updates this element's [CanvasElement.enabled] flag and [CanvasElement.style] for a 6-frame
 * horizontal checkbox sprite strip (frame 0 = unchecked hovered, 1 = unchecked rest,
 * 2 = checked hovered/pressed, 3 = checked rest, 4 = unchecked disabled, 5 = checked disabled).
 *
 * Frame width is taken from the current `style.image.regionWidth` when positive, or `width.toInt()`
 * otherwise. Does not reallocate [CanvasElement.style] when the frames already match.
 *
 * @param checked Whether the checkbox is currently checked.
 * @param enabled Whether the checkbox is enabled and interactive.
 */
fun CanvasElement.applySixStateCheckboxStrip(checked: Boolean, enabled: Boolean = true) {
    this.enabled = enabled
    val disabledFrame = if (checked) SIX_STATE_CHECKED_DISABLED_FRAME else SIX_STATE_UNCHECKED_DISABLED_FRAME
    val activeRest = if (checked) SIX_STATE_CHECKED_REST_FRAME else SIX_STATE_UNCHECKED_REST_FRAME
    val activeHover = if (checked) SIX_STATE_CHECKED_HOVERED_FRAME else SIX_STATE_UNCHECKED_HOVERED_FRAME
    applyHorizontalStripFrames(
        restFrame = if (enabled) activeRest else disabledFrame,
        hoveredFrame = if (enabled) activeHover else disabledFrame,
        pressedFrame = if (enabled) SIX_STATE_CHECKED_HOVERED_FRAME else disabledFrame,
        disabledFrame = disabledFrame,
    )
}

private fun CanvasElement.applyHorizontalStripFrames(
    restFrame: Int,
    hoveredFrame: Int,
    pressedFrame: Int,
    disabledFrame: Int,
) {
    val base = style.image ?: return
    val stride = if (base.regionWidth > 0) base.regionWidth else width.toInt()
    if (stride <= 0) return
    val restImage = base.copy(regionX = restFrame * stride, regionWidth = stride)
    val hoveredImage = base.copy(regionX = hoveredFrame * stride, regionWidth = stride)
    val pressedImage = base.copy(regionX = pressedFrame * stride, regionWidth = stride)
    val disabledImage = base.copy(regionX = disabledFrame * stride, regionWidth = stride)
    if (!hasMatchingStateImages(restImage, hoveredImage, pressedImage, disabledImage)) {
        style = style.copy(
            image = restImage,
            hovered = (style.hovered ?: CanvasStateStyle()).copy(image = hoveredImage),
            pressed = (style.pressed ?: CanvasStateStyle()).copy(image = pressedImage),
            disabled = (style.disabled ?: CanvasStateStyle()).copy(image = disabledImage),
        )
    }
}

private fun CanvasElement.hasMatchingStateImages(
    restImage: CanvasImage,
    hoveredImage: CanvasImage,
    pressedImage: CanvasImage,
    disabledImage: CanvasImage,
): Boolean = style.image == restImage &&
    style.hovered?.image == hoveredImage &&
    style.pressed?.image == pressedImage &&
    style.disabled?.image == disabledImage

private fun Color.toCanvasHexString(): String {
    if (this == Color.White) return "#FFFFFF"
    val rByte = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    val gByte = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    val bByte = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    val aByte = (a.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    return if (aByte == 255) {
        "#${hexByte(rByte)}${hexByte(gByte)}${hexByte(bByte)}"
    } else {
        "#${hexByte(rByte)}${hexByte(gByte)}${hexByte(bByte)}${hexByte(aByte)}"
    }
}

private fun hexByte(value: Int): String =
    value.coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')

private const val FOUR_STATE_HOVERED_FRAME = 0
private const val FOUR_STATE_REST_FRAME = 1
private const val FOUR_STATE_PRESSED_FRAME = 2
private const val FOUR_STATE_DISABLED_FRAME = 3

private const val SIX_STATE_UNCHECKED_HOVERED_FRAME = 0
private const val SIX_STATE_UNCHECKED_REST_FRAME = 1
private const val SIX_STATE_CHECKED_HOVERED_FRAME = 2
private const val SIX_STATE_CHECKED_REST_FRAME = 3
private const val SIX_STATE_UNCHECKED_DISABLED_FRAME = 4
private const val SIX_STATE_CHECKED_DISABLED_FRAME = 5
