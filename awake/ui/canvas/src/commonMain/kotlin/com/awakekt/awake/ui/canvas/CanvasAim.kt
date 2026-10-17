/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * Drag to aim: while an action button is held, dragging away from it aims, and letting go fires the
 * button's action with that aim, unless the thumb is let go in the [cancel] zone.
 *
 * The aim is the drag from where the press landed, as a share of [radius], up being negative as on
 * screen: [CanvasElement.aimX] and [CanvasElement.aimY]. A drag inside [deadZone] is no aim, so a
 * quick tap fires at whatever the game picks, such as the nearest target.
 *
 * @property mode Whether the aim is a [CanvasAimMode.Direction], always full length once past the
 * dead zone, or a [CanvasAimMode.Ground] target, as far out as the drag.
 * @property radius How far, in dp, the thumb drags for a full-length aim. 0 takes
 * [DEFAULT_RADIUS].
 * @property deadZone The share of [radius] near where the press landed that is no aim; from 0 up
 * to, but not including, 1.
 * @property cancel Where letting go cancels the shot instead of firing it, drawn while aiming; null
 * has none, and only a release back inside the dead zone of a button that needs an aim cancels.
 * @property aimAction An input axis action pushed with the aim, up being positive, while aiming and
 * on the frame the action fires; empty leaves the aim for the game to read.
 * @property lockTarget Whether the element's [CanvasElement.aimLock] is asked to lock the aim onto a
 * target while aiming.
 */
@Serializable
data class CanvasAim(
    val mode: CanvasAimMode = CanvasAimMode.Direction,
    @PropertyRange(min = 0.0) val radius: Float = 0f,
    @PropertyRange(min = 0.0, max = 1.0, exclusiveMax = true) val deadZone: Float = DEFAULT_DEAD_ZONE,
    val cancel: CanvasZone? = null,
    val aimAction: String = "",
    val lockTarget: Boolean = false,
) {
    /** What is wrong with these settings, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        if (radius < 0f) add("radius must not be negative")
        if (deadZone < 0f || deadZone >= 1f) add("deadZone must be at least 0 and below 1")
        cancel?.problems()?.forEach { add("cancel.$it") }
    }

    /** The drag's reach in dp. */
    val reach: Float get() = if (radius > 0f) radius else DEFAULT_RADIUS

    /**
     * The aim for a drag of ([dx], [dy]) dp, as `(x, y)` from -1 to 1, or zero inside the dead zone:
     * a unit direction in [CanvasAimMode.Direction], and as far out as the drag in
     * [CanvasAimMode.Ground].
     */
    fun aim(dx: Float, dy: Float): Pair<Float, Float> {
        val length = sqrt(dx * dx + dy * dy)
        val share = length / reach
        if (length == 0f || share <= deadZone) return Pair(0f, 0f)
        val scale = when (mode) {
            CanvasAimMode.Direction -> 1f / length
            CanvasAimMode.Ground -> share.coerceAtMost(1f) / length
        }
        return Pair(dx * scale, dy * scale)
    }

    /** Defaults. */
    companion object {
        /** How far a full-length aim drags, in dp, when [radius] is 0. */
        const val DEFAULT_RADIUS = 120f

        /** The share of the reach that is no aim, by default. */
        const val DEFAULT_DEAD_ZONE = 0.2f
    }
}

/** What an aimed button's aim means. */
@Serializable
enum class CanvasAimMode {
    /** A direction only: past the dead zone, any drag aims at full length. */
    Direction,

    /** A spot on the ground: the further the drag, the further out, up to the full reach. */
    Ground,
}

/**
 * A box on the canvas, placed the way an element is: against its [anchor], moved in by [offsetX]
 * and [offsetY], [width] by [height] dp, inside the safe area.
 */
@Serializable
data class CanvasZone(
    val anchor: CanvasAnchor = CanvasAnchor.TopCenter,
    val offsetX: Float = 0f,
    val offsetY: Float = DEFAULT_OFFSET_Y,
    @PropertyRange(min = 0.0) val width: Float = DEFAULT_SIZE,
    @PropertyRange(min = 0.0) val height: Float = DEFAULT_SIZE,
) {
    /** What is wrong with this zone, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        if (width < 0f || height < 0f) add("size must not be negative")
    }

    private companion object {
        const val DEFAULT_OFFSET_Y = 24f
        const val DEFAULT_SIZE = 72f
    }
}

/**
 * What [CanvasElement.aimLock] locked an aim onto.
 *
 * @property key The target, as the game knows it.
 * @property x The aim that points at it, from -1 to 1.
 * @property y The vertical half of [x]; up is negative, as on screen.
 */
data class CanvasAimTarget(val key: String, val x: Float, val y: Float)

/**
 * Locks a drag-to-aim onto a target, such as the enemy nearest the aimed line: given the button and
 * its raw aim, it answers the target to aim at instead, or null to keep the raw aim.
 */
fun interface CanvasAimLock {
    /** The target for [element]'s raw aim ([x], [y]), or null for none. */
    fun lock(element: CanvasElement, x: Float, y: Float): CanvasAimTarget?
}
