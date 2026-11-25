/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.quality

import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.render.renderer.Renderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * How large textures upload: the top mip levels a mipmapped texture leaves out, for a device short
 * of memory (#1660). Saves GPU memory, not decode memory, and applies to textures uploaded after it
 * changes; see [Renderer.textureMipSkip].
 *
 * @property mipSkip The top mip levels left out at upload.
 */
enum class TextureQuality(val mipSkip: Int) {
    /** Full size. */
    High(0),

    /** Half size: the top level left out. */
    Medium(1),

    /** Quarter size: the top two levels left out. */
    Low(2),
}

/**
 * How many samples anisotropic filtering takes, for ground and walls seen at a grazing angle (#1660).
 * Clamped to [Renderer.maxSupportedAnisotropy]; see [Renderer.maxAnisotropy].
 *
 * @property samples The most samples a texture takes, 1 being off.
 */
enum class TextureAnisotropy(val samples: Int) {
    /** No anisotropic filtering. */
    Off(1),

    /** Up to 2 samples. */
    X2(2),

    /** Up to 4 samples. */
    X4(4),

    /** Up to 8 samples. */
    X8(8),

    /** Up to 16 samples. */
    X16(16),
}

/**
 * Where an app keeps its [TextureQuality] and [TextureAnisotropy] between runs. Core stores nothing
 * itself: the app passes this in `app { textureSettings { persistence = ... } }`.
 */
interface TextureSettingsPersistence {
    /** The quality saved by an earlier run, or `null` when there is none. */
    fun loadQuality(): TextureQuality?

    /** The filtering saved by an earlier run, or `null` when there is none. */
    fun loadAnisotropy(): TextureAnisotropy?

    /** Called each time the quality changes to a different value. */
    fun saveQuality(quality: TextureQuality)

    /** Called each time the filtering changes to a different value. */
    fun saveAnisotropy(anisotropy: TextureAnisotropy)
}

/**
 * One app's texture settings, next to [RenderQualitySettings]: session scoped, registered as a
 * service, and applied by `GraphicsEngine` before `ready()`, before `resume()` on surface restore,
 * and before every frame. An app that registers none renders exactly as before:
 * [Renderer.maxAnisotropy] stays 1 and [Renderer.textureMipSkip] 0.
 *
 * @param persistence Loads the saved values and saves each change; `null` keeps them in memory only.
 * @param initialQuality The quality when nothing is saved.
 * @param initialAnisotropy The filtering when nothing is saved.
 */
class TextureSettings(
    private val persistence: TextureSettingsPersistence? = null,
    initialQuality: TextureQuality = TextureQuality.High,
    initialAnisotropy: TextureAnisotropy = TextureAnisotropy.X8,
) {
    private val qualityState = MutableStateFlow(persistence?.loadQuality() ?: initialQuality)
    private val anisotropyState = MutableStateFlow(persistence?.loadAnisotropy() ?: initialAnisotropy)

    /** The selected texture quality. */
    val quality: StateFlow<TextureQuality> = qualityState.asStateFlow()

    /** The selected anisotropic filtering. */
    val anisotropy: StateFlow<TextureAnisotropy> = anisotropyState.asStateFlow()

    /** Selects [quality], saving it through the persistence callback when it changed. */
    fun select(quality: TextureQuality) {
        val previous = qualityState.getAndUpdate { quality }
        if (previous != quality) persistence?.saveQuality(quality)
    }

    /** Selects [anisotropy], saving it through the persistence callback when it changed. */
    fun select(anisotropy: TextureAnisotropy) {
        val previous = anisotropyState.getAndUpdate { anisotropy }
        if (previous != anisotropy) persistence?.saveAnisotropy(anisotropy)
    }

    /**
     * Sets [renderer]'s [Renderer.maxAnisotropy] and [Renderer.textureMipSkip] from the selection,
     * writing only a value that changed. Allocation free; called once per frame.
     */
    fun applyTo(renderer: Renderer) {
        val samples = anisotropyState.value.samples
        if (renderer.maxAnisotropy != samples) renderer.maxAnisotropy = samples
        val skip = qualityState.value.mipSkip
        if (renderer.textureMipSkip != skip) renderer.textureMipSkip = skip
    }

    /** The suggested starting values for a device. */
    companion object {
        /**
         * The suggested starting quality for [deviceClass]: Medium on a mobile GPU, which is short of
         * memory, High elsewhere.
         */
        fun defaultQuality(deviceClass: GpuDeviceClass): TextureQuality =
            if (deviceClass == GpuDeviceClass.Mobile) TextureQuality.Medium else TextureQuality.High

        /** The suggested starting filtering for [deviceClass]: 4 samples on a mobile GPU, 8 elsewhere. */
        fun defaultAnisotropy(deviceClass: GpuDeviceClass): TextureAnisotropy =
            if (deviceClass == GpuDeviceClass.Mobile) TextureAnisotropy.X4 else TextureAnisotropy.X8
    }
}
