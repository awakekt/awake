/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.render.texture.TextureRegion

/**
 * One content-group texture binding's base-level update.
 * @property binding Destination sampled-image binding.
 * @property region Checked RGBA8 base-level source rectangle.
 */
data class ContentTextureUpdate(val binding: Int, val region: TextureRegion)

/**
 * Single-owner source of updates for a fenced frame slot. Mutable bindings are replicated per
 * Vulkan frame slot; WebGPU queue ordering preserves submitted frames. Track revision cursors
 * per frame index and return no updates when that slot already holds the desired snapshot.
 */
interface ContentTextureUpdates {
    /** Fixed set of mutable sampled-image bindings. */
    val bindings: Set<Int>

    /** Returns bounded region writes for the specified writable frame slot. */
    fun updates(frameIndex: Int): List<ContentTextureUpdate>
}
