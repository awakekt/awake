/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

/**
 * What a `day_cycle` writes for the sun and moon's visibility: the stops' `sunVisibility` and
 * `moonVisibility` blended by time of day, so each shows only in its own hours and both can be hidden
 * for a while at dusk. A body no stop sets stays fully visible.
 */
internal class BodyVisibilitySpans {
    private val sun = StopSpan { it.sunVisibility }
    private val moon = StopSpan { it.moonVisibility }

    fun apply(cycle: DayCycle, stops: List<SceneDayStop>, time: Float) {
        if (stops.isEmpty()) return
        if (sun.find(stops, time)) cycle.sunVisibility = sun.value()
        if (moon.find(stops, time)) cycle.moonVisibility = moon.value()
    }
}
