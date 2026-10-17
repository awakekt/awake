/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A player's own layout of the on-screen controls, saved as data: every touch control scaled and
 * faded together, and each control named by its [CanvasElement.controlId] moved, scaled and faded on
 * its own. A settings screen edits it and saves [toJson]; the game loads it with [fromJson] and sets
 * it as [CanvasControls.playerLayout].
 *
 * A control's own adjustment is on top of the layout's: its scale times [scale], its opacity times
 * [opacity]. Moving one keeps its anchor: an offset moves it from where the scene placed it.
 *
 * @property scale How big every touch control is, 1 as the scene made it.
 * @property opacity How opaque every touch control is, from 0 to 1.
 * @property controls Each control's own adjustment, by its [CanvasElement.controlId].
 */
@Serializable
data class CanvasPlayerLayout(
    @PropertyRange(min = 0.0, exclusiveMin = true) val scale: Float = 1f,
    @PropertyRange(min = 0.0, max = 1.0) val opacity: Float = 1f,
    val controls: Map<String, CanvasControlAdjust> = emptyMap(),
) {
    /** What is wrong with this layout, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        if (scale <= 0f) add("scale must be above 0")
        if (opacity !in 0f..1f) add("opacity must be between 0 and 1")
        for ((id, adjust) in controls) adjust.problems().forEach { add("controls.$id.$it") }
    }

    /** This layout with control [id] moved by ([dx], [dy]) dp more, as a drag on a settings screen does. */
    fun moved(id: String, dx: Float, dy: Float): CanvasPlayerLayout {
        val adjust = controls[id] ?: CanvasControlAdjust()
        return copy(controls = controls + (id to adjust.copy(offsetX = adjust.offsetX + dx, offsetY = adjust.offsetY + dy)))
    }

    /** This layout as JSON, to save. */
    fun toJson(): String = json.encodeToString(serializer(), this)

    /** How [element] is adjusted: null for one the layout leaves as it is. */
    internal fun adjustOf(element: CanvasElement): CanvasControlAdjust? {
        val own = if (element.controlId.isEmpty()) null else controls[element.controlId]
        val all = element.touchOnly && (scale != 1f || opacity != 1f)
        if (own == null && !all) return null
        val base = own ?: CanvasControlAdjust.None
        return if (element.touchOnly) base.copy(scale = base.scale * scale, opacity = base.opacity * opacity) else base
    }

    /** Reading a saved layout. */
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The layout [text] saved by [toJson]; a field it does not have keeps its default. */
        fun fromJson(text: String): CanvasPlayerLayout = json.decodeFromString(serializer(), text)
    }
}

/**
 * How a player moved, scaled and faded one control.
 *
 * @property offsetX How far right, in dp, from where the scene placed it; negative moves it left.
 * @property offsetY How far down, in dp, from where the scene placed it; negative moves it up.
 * @property scale How big it is, 1 as the scene made it.
 * @property opacity How opaque it is, from 0 to 1.
 */
@Serializable
data class CanvasControlAdjust(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    @PropertyRange(min = 0.0, exclusiveMin = true) val scale: Float = 1f,
    @PropertyRange(min = 0.0, max = 1.0) val opacity: Float = 1f,
) {
    internal fun problems(): List<String> = buildList {
        if (scale <= 0f) add("scale must be above 0")
        if (opacity !in 0f..1f) add("opacity must be between 0 and 1")
    }

    internal companion object {
        val None = CanvasControlAdjust()
    }
}
