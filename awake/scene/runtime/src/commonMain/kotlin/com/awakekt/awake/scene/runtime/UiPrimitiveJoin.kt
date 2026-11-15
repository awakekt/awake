/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.graphics2d.UiDrawPrimitive

/**
 * A frame's UI primitives after the ones systems staged that frame, joined without copying a list
 * per frame (#1621): the built list itself when nothing was staged, otherwise one list kept and
 * refilled, once a frame however often it is asked for. Joining with `+` copied every primitive
 * twice a frame, about 8 B per primitive.
 *
 * The joined list is this frame's: the next [join] after [reset] refills it.
 */
internal class UiPrimitiveJoin {
    private val joined = ArrayList<UiDrawPrimitive>()
    private var joinedFrom: List<UiDrawPrimitive>? = null

    /** [staged] then [built]: [built] itself when [staged] is empty. */
    fun join(staged: List<UiDrawPrimitive>, built: List<UiDrawPrimitive>): List<UiDrawPrimitive> {
        if (staged.isEmpty()) return built
        if (joinedFrom !== built) {
            joinedFrom = built
            joined.clear()
            // Indexed: `addAll` copies its argument into a new array first.
            for (i in staged.indices) joined.add(staged[i])
            for (i in built.indices) joined.add(built[i])
        }
        return joined
    }

    /** Starts a frame: the next [join] builds the list again, as what was staged has changed. */
    fun reset() {
        joinedFrom = null
    }
}
