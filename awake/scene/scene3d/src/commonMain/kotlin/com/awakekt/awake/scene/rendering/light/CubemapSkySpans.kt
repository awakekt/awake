/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.scene.rendering.sky.Skybox

/**
 * What a `day_cycle` writes to a cubemap sky: the tint and exposure blended from the stops, and
 * which two panoramas are showing and how far the first has moved to the second.
 *
 * It writes play state only (see [Skybox.Mode.Cubemap.tint]), so the sky saves as authored. Nothing
 * is allocated per frame: the tint is replaced only when it moves by a [COLOR_STEP].
 */
internal class CubemapSkySpans {
    private val tint = StopSpan { it.skyTint }
    private val exposure = StopSpan { it.skyExposure }

    fun apply(sky: Skybox.Mode.Cubemap, stops: List<SceneDayStop>, time: Float) {
        if (tint.find(stops, time)) sky.tint = tint.color(sky.tint)
        if (exposure.find(stops, time)) sky.dayExposure = exposure.value()
        val panoramas = sky.panoramas
        if (panoramas.size < 2) {
            sky.blendFrom = 0
            sky.blendTo = 0
            sky.blend = 0f
            return
        }
        // The last panorama at or before the time leads; before the day's first, yesterday's last does.
        var before = -1
        for (index in panoramas.indices) if (panoramas[index].time <= time) before = index
        val from = if (before >= 0) before else panoramas.lastIndex
        val to = (from + 1) % panoramas.size
        val gap = (panoramas[to].time - panoramas[from].time).mod(1f)
        val fraction = if (gap > 0f) (time - panoramas[from].time).mod(1f) / gap else 0f
        sky.blendFrom = from
        sky.blendTo = to
        sky.blend = fraction
    }
}
