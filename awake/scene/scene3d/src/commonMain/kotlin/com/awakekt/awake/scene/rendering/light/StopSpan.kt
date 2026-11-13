/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.lerp
import com.awakekt.awake.scene.document.SceneColor
import kotlin.math.abs

/** One stop field's values either side of a time of day, and how far between them the time is. */
internal class StopSpan<T : Any>(private val field: (SceneDayStop) -> T?) {
    lateinit var from: T
    lateinit var to: T
    var fraction = 0f

    /** Finds the stops around [time] that set this field; false when none does. */
    fun find(stops: List<SceneDayStop>, time: Float): Boolean {
        var first = -1
        var last = -1
        var before = -1
        var after = -1
        for (index in stops.indices) {
            if (field(stops[index]) == null) continue
            if (first < 0) first = index
            last = index
            if (stops[index].time <= time) {
                before = index
            } else if (after < 0) {
                after = index
            }
        }
        // Before the day's first stop, yesterday's last one leads in; after its last, tomorrow's first follows.
        if (first >= 0) between(stops[if (before >= 0) before else last], stops[if (after >= 0) after else first], time)
        return first >= 0
    }

    private fun between(fromStop: SceneDayStop, toStop: SceneDayStop, time: Float) {
        from = checkNotNull(field(fromStop))
        to = checkNotNull(field(toStop))
        val gap = (toStop.time - fromStop.time).mod(1f)
        fraction = if (gap > 0f) (time - fromStop.time).mod(1f) / gap else 0f
    }
}

internal fun StopSpan<Float>.value(): Float = lerp(from, to, fraction)

internal fun StopSpan<SceneColor>.colorInto(target: Vec3f) {
    target.set(lerp(from.r, to.r, fraction), lerp(from.g, to.g, fraction), lerp(from.b, to.b, fraction))
}

/** The blended colour, or [current] while it is within [COLOR_STEP] of it. */
internal fun StopSpan<SceneColor>.color(current: Color): Color {
    val r = lerp(from.r, to.r, fraction)
    val g = lerp(from.g, to.g, fraction)
    val b = lerp(from.b, to.b, fraction)
    val a = lerp(from.a, to.a, fraction)
    val moved = maxOf(maxOf(abs(current.r - r), abs(current.g - g)), maxOf(abs(current.b - b), abs(current.a - a)))
    return if (moved < COLOR_STEP) current else Color(r, g, b, a)
}
