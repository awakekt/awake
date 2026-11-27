/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.compose

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.graphics2d.FilterQuality
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering

/**
 * GPU textures for the [ImageBitmap]s a UI draws, uploaded the first frame each one appears and
 * evicted once it stops being drawn.
 *
 * Unused entries are kept for a brief grace window ([minIdleFrames] frames so submitted GPU frames
 * finish reading them, up to [maxIdleFrames] frames while idle pixels stay within [maxIdleBytes])
 * so brief UI toggles do not re-upload every frame while transient or replaced bitmaps release both
 * their managed pixel arrays and their renderer-owned GPU textures.
 */
internal class ImageTextures(
    private val minIdleFrames: Long = DEFAULT_MIN_IDLE_FRAMES,
    private val maxIdleFrames: Long = DEFAULT_MAX_IDLE_FRAMES,
    private val maxIdleBytes: Long = DEFAULT_MAX_IDLE_BYTES,
) {
    // One texture per image and filter: an image drawn crisp in one place and smooth in another
    // samples two textures, since the filter is part of a texture's sampler. So is the mip chain:
    // a smooth image drawn both with and without its mips is uploaded twice.
    private val entries = LinkedHashMap<ImageKey, Entry>()
    private var frame = 0L
    private var usedThisFrame = 0

    internal val size: Int get() = entries.size

    /**
     * The material that samples [image] with [filterQuality]. [baseLevelOnly] leaves a smooth
     * image without its mip chain, sampling only its full-size level the way a render target is
     * sampled; a crisp image has no mips either way.
     */
    fun materialFor(
        renderer: Renderer,
        image: ImageBitmap,
        filterQuality: FilterQuality = FilterQuality.Low,
        baseLevelOnly: Boolean = false,
    ): Material {
        val filtering = when {
            filterQuality == FilterQuality.None -> TextureFiltering.Nearest
            baseLevelOnly -> TextureFiltering.BaseLevelLinear
            else -> TextureFiltering.Linear
        }
        val key = ImageKey(image, filterQuality, filtering == TextureFiltering.BaseLevelLinear)
        val existing = entries[key]
        if (existing != null) {
            markUsed(existing)
            return existing.material
        }
        // UI images always upload at full resolution, exempt from Renderer.textureMipSkip (#1660).
        val previousMipSkip = renderer.textureMipSkip
        if (previousMipSkip != 0) renderer.textureMipSkip = 0
        val material = try {
            renderer.createMaterial(
                texture = TextureAsset(image.pixels, image.width, image.height, filtering = filtering),
            )
        } finally {
            if (previousMipSkip != 0) renderer.textureMipSkip = previousMipSkip
        }
        val byteSize = image.width.toLong() * image.height.toLong() * ImageBitmap.BYTES_PER_PIXEL
        val created = Entry(material, byteSize, frame)
        entries[key] = created
        usedThisFrame += 1
        return material
    }

    /**
     * Advances the frame counter and destroys cached materials for images that have been idle past
     * [maxIdleFrames], or past [minIdleFrames] when idle images exceed [maxIdleBytes].
     */
    fun endFrame() {
        if (entries.isNotEmpty() && usedThisFrame < entries.size) {
            evictIdleEntries()
        }
        frame += 1L
        usedThisFrame = 0
    }

    fun dispose() {
        entries.values.forEach { it.material.destroy() }
        entries.clear()
        usedThisFrame = 0
    }

    private fun markUsed(entry: Entry) {
        if (entry.lastUsedFrame != frame) {
            entry.lastUsedFrame = frame
            usedThisFrame += 1
        }
    }

    private fun evictIdleEntries() {
        var idleBytes = 0L
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            val idle = frame - entry.lastUsedFrame
            if (idle >= maxIdleFrames) {
                entry.material.destroy()
                iterator.remove()
            } else if (idle > 0L) {
                idleBytes += entry.byteSize
            }
        }
        if (idleBytes > maxIdleBytes) {
            evictOldestIdleOverBudget(idleBytes)
        }
    }

    private fun evictOldestIdleOverBudget(initialIdleBytes: Long) {
        var remaining = initialIdleBytes
        var nextKey = findOldestEvictableKey()
        while (remaining > maxIdleBytes && nextKey != null) {
            val removed = entries.remove(nextKey)
            if (removed != null) {
                removed.material.destroy()
                remaining -= removed.byteSize
            }
            nextKey = if (remaining > maxIdleBytes) findOldestEvictableKey() else null
        }
    }

    private fun findOldestEvictableKey(): ImageKey? {
        var oldestKey: ImageKey? = null
        var oldestFrame = Long.MAX_VALUE
        for ((key, entry) in entries) {
            val idle = frame - entry.lastUsedFrame
            if (idle >= minIdleFrames && entry.lastUsedFrame < oldestFrame) {
                oldestFrame = entry.lastUsedFrame
                oldestKey = key
            }
        }
        return oldestKey
    }

    private data class ImageKey(
        val image: ImageBitmap,
        val filterQuality: FilterQuality,
        val baseLevelOnly: Boolean,
    )

    private class Entry(
        val material: Material,
        val byteSize: Long,
        var lastUsedFrame: Long,
    )

    internal companion object {
        /** Clear of 2-3 frames in flight before releasing an idle GPU texture. */
        const val DEFAULT_MIN_IDLE_FRAMES: Long = 2L

        /** Idle grace window (~1 s at 60 FPS) for small warm UI bitmaps. */
        const val DEFAULT_MAX_IDLE_FRAMES: Long = 60L

        /** Idle pixel budget before large unused bitmaps are evicted at [DEFAULT_MIN_IDLE_FRAMES]. */
        const val DEFAULT_MAX_IDLE_BYTES: Long = 4L * 1024L * 1024L
    }
}
