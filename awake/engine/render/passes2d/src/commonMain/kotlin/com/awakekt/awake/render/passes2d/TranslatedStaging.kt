/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.isTranslatable
import com.awakekt.awake.core.graphics2d.movedClip

/**
 * Checks that a span inside a translate range can be staged and then moved: a kind whose every
 * vertex starts with its pixel position, and no path clip, which clips in place rather than moved.
 */
internal fun checkTranslatable(first: DrawCommand, activePathClips: List<DrawPath>) {
    check(first.isTranslatable) { "$first cannot sit inside a translate range" }
    check(activePathClips.isEmpty()) { "a path clip is active inside a translate range" }
}

/**
 * Moves the clip runs staged from index [from] on by ([dx], [dy]). The geometry was staged moved
 * already, each primitive's position moved before anything was added to it, as a moved copy of it
 * would be, so its vertices are bit-identical to that copy's; a clip rect has no primitive to move.
 */
internal fun moveStagedClips(runs: MutableList<StagedDrawRun>, from: Int, dx: Float, dy: Float) {
    for (index in from until runs.size) {
        val run = runs[index]
        if (run is StagedDrawRun.ClipRun) runs[index] = StagedDrawRun.ClipRun(run.rect.movedClip(dx, dy))
    }
}
