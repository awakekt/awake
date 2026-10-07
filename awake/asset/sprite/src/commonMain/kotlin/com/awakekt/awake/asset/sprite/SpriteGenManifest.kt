/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.sprite

import com.awakekt.awake.core.animation.FrameClip
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Imports sprite-gen's `manifest.json` into regular-grid sprite data, including multi-row clips. */
object SpriteGenManifest {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Reads the chosen `game_input`, `frame_layout` and named `animation.rows` from [text].
     * Each run starts on its declared row and contains contiguous, untrimmed grid cells in reading
     * order, wrapping to the next row when it reaches the sheet's right edge. Uniform
     * `durations_ms` take precedence over `fps`; variable frame durations are rejected because
     * [FrameClip] plays at a fixed rate. Throws [IllegalArgumentException] for unsupported data.
     */
    fun decode(text: String): SpriteSheet {
        val source = json.decodeFromString<Manifest>(text)
        val layout = source.layout
        // Validate dimensions before any division or frame-index arithmetic.
        val grid = SpriteSheet(source.image, layout.sheetWidth, layout.sheetHeight, layout.cellWidth, layout.cellHeight, emptyMap())
        val animation = source.animation
        require(animation.cellWidth == grid.cellWidth && animation.cellHeight == grid.cellHeight && animation.columns == grid.columns) {
            "sprite-gen animation dimensions disagree with frame_layout."
        }
        require(layout.rows.isNotEmpty() && layout.rows.keys == animation.rows.keys) {
            "sprite-gen frame_layout.rows and animation.rows must name the same non-empty set of clips."
        }
        val clips = layout.rows.mapValues { (name, frames) ->
            require(name.isNotBlank()) { "sprite-gen clip names must not be blank." }
            val run = animation.rows.getValue(name)
            require(run.row in 0 until grid.rows && run.frames > 0 && run.frames == frames.size) {
                "sprite-gen clip $name has an invalid row or frame count."
            }
            val first = firstFrameIndex(name, frames, run.row, grid)
            FrameClip(first, frames.size, run.rate(name), run.loop)
        }
        return SpriteSheet(source.image, grid.width, grid.height, grid.cellWidth, grid.cellHeight, clips)
    }
}

private fun firstFrameIndex(name: String, frames: List<Frame>, row: Int, grid: SpriteSheet): Int {
    var first = 0
    for (offset in frames.indices) {
        val frame = frames[offset]
        require(frame.w == grid.cellWidth && frame.h == grid.cellHeight) { "sprite-gen clip $name contains a trimmed or resized cell." }
        require(frame.x >= 0 && frame.y >= 0 && frame.x.toLong() + frame.w <= grid.width && frame.y.toLong() + frame.h <= grid.height) {
            "sprite-gen clip $name has a cell outside the sheet."
        }
        require(frame.x % grid.cellWidth == 0 && frame.y % grid.cellHeight == 0) {
            "sprite-gen clip $name has a cell off its declared grid."
        }
        val frameRow = frame.y / grid.cellHeight
        val index = frameRow * grid.columns + frame.x / grid.cellWidth
        if (offset == 0) {
            require(frameRow == row) { "sprite-gen clip $name must start on its declared row." }
            first = index
        }
        require(index.toLong() == first.toLong() + offset) {
            "sprite-gen clip $name must contain contiguous cells in playback order."
        }
    }
    return first
}

@Serializable
private data class Manifest(
    @SerialName("game_input") val image: String,
    @SerialName("frame_layout") val layout: Layout,
    val animation: Animation,
)

@Serializable
private data class Layout(
    val sheetWidth: Int,
    val sheetHeight: Int,
    val cellWidth: Int,
    val cellHeight: Int,
    val rows: Map<String, List<Frame>>,
)

@Serializable
private data class Frame(val x: Int, val y: Int, val w: Int, val h: Int)

@Serializable
private data class Animation(val cellWidth: Int, val cellHeight: Int, val columns: Int, val rows: Map<String, Run>)

@Serializable
private data class Run(
    val row: Int,
    val frames: Int,
    val fps: Float? = null,
    @SerialName("durations_ms") val durations: List<Float>? = null,
    val loop: Boolean = true,
) {
    fun rate(name: String): Float {
        require(fps == null || fps.isFinite() && fps >= 0f) { "sprite-gen clip $name has an invalid fps." }
        val times = durations ?: return requireNotNull(fps) { "sprite-gen clip $name must specify fps or durations_ms." }
        require(times.size == frames && times.all { it.isFinite() && it > 0f }) {
            "sprite-gen clip $name durations_ms must contain one positive finite duration per frame."
        }
        require(times.all { it == times.first() }) { "sprite-gen clip $name has variable durations; export a uniform frame rate." }
        return MILLISECONDS_PER_SECOND / times.first()
    }
}

private const val MILLISECONDS_PER_SECOND = 1000f
