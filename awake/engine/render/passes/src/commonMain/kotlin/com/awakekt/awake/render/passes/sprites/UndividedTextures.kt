/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.sprites

/**
 * The textures already logged as not dividing into their cells, by name, so each is logged once however
 * many sprites or tilemap layers draw it. [SpriteRenderBatch] keeps one for its sheets. Tilemap layers
 * given the same one, as `TilemapRenderBatch(undivided = ...)`, log an atlas once between them, as the
 * scene runtime's do. Forgetting a texture lets it be logged again if it still doesn't divide.
 *
 * Use it on the frame thread only, as the batches are.
 */
class UndividedTextures {
    private val logged = mutableSetOf<String>()

    /**
     * Records that the texture [name] doesn't divide into its cells.
     *
     * @return `true` the first time, when the caller logs it; `false` once it has been, until it is forgotten.
     */
    fun firstReport(name: String): Boolean = logged.add(name)

    /** Forgets that [name] was logged, such as when its file changed, so it is logged again if it still doesn't divide. */
    fun forget(name: String) {
        logged.remove(name)
    }

    /** [forget] for every texture. */
    fun forgetAll() {
        logged.clear()
    }
}
