/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.skin

import com.awakekt.awake.compose.foundation.sheet.StateSheet
import com.awakekt.awake.compose.ui.graphics.ImageFill

/**
 * The name of one part of a [Skin], typed by what the part resolves to ([R]). A component declares
 * its keys once, as top-level values, and draws by key: a part is never looked up by a string, and
 * the [name] is only for messages. Keys are compared by identity, so declare each once.
 *
 *     val ButtonFace = StatesKey("button.face")
 *     val WindowFrame = FrameKey("window.frame")
 */
sealed class PartKey<R : Any>(
    /** What error messages call this part. */
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "A part key needs a name." }
    }

    override fun toString(): String = "${this::class.simpleName}($name)"
}

/** A part drawn as one [ImageFill]: a window frame, a panel, a slot. */
class FrameKey(name: String) : PartKey<ImageFill>(name)

/** A part with one frame per interaction state, as a [StateSheet]: a button, a tab, a toggle. */
class StatesKey(name: String) : PartKey<StateSheet>(name)

/** A part drawn as the fill of a gauge by [drawImageFillBar][com.awakekt.awake.compose.ui.graphics.drawImageFillBar]. */
class BarKey(name: String) : PartKey<ImageFill>(name)
