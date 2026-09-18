/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import com.awakekt.awake.core.color.Color

val DefaultHorizonColor = Color(r = 0.72f, g = 0.80f, b = 0.88f, a = 1f)
val DefaultZenithColor = Color(r = 0.20f, g = 0.38f, b = 0.68f, a = 1f)

/**
 * Skybox component — defines the atmospheric sky gradient or sky appearance behind the scene.
 *
 * Matches Godot's `Sky` resource / Unreal's `SkyAtmosphere`.
 *
 * @property enabled Whether procedural skybox and atmospheric horizon are rendered.
 * @property mode The active skybox rendering mode (procedural gradient, cubemap texture, or solid color).
 */
data class Skybox(
    var enabled: Boolean = true,
    var mode: Mode = Mode.Procedural(),
) {
    /**
     * Discriminated skybox representation modes.
     */
    sealed interface Mode {
        /**
         * Two-color gradient skybox.
         *
         * @property horizonColor Atmospheric horizon gradient color.
         * @property zenithColor Atmospheric upper sky (zenith) color.
         */
        data class Procedural(
            var horizonColor: Color = DefaultHorizonColor,
            var zenithColor: Color = DefaultZenithColor,
        ) : Mode

        /**
         * Sampled 6-face cubemap environment texture.
         *
         * @property assetPath Logical or filesystem path to the cubemap asset.
         * @property exposure Exposure multiplier applied during sampling.
         */
        data class Cubemap(
            var assetPath: String = "",
            var exposure: Float = 1.0f,
        ) : Mode

        /**
         * Uniform solid background color.
         *
         * @property color Solid background clear color.
         */
        data class SolidColor(
            var color: Color = DefaultHorizonColor,
        ) : Mode
    }

    /**
     * Backward-compatible enumeration of skybox modes.
     */
    enum class Type {
        Procedural,
        Cubemap,
        SolidColor,
    }

    /**
     * Convenience constructor preserving backward compatibility with legacy property sets.
     */
    constructor(
        enabled: Boolean = true,
        horizonColor: Color = DefaultHorizonColor,
        zenithColor: Color = DefaultZenithColor,
        cubemapPath: String? = null,
        exposure: Float = 1.0f,
        type: Type = Type.Procedural,
    ) : this(
        enabled = enabled,
        mode = when (type) {
            Type.Procedural -> Mode.Procedural(horizonColor, zenithColor)
            Type.Cubemap -> Mode.Cubemap(cubemapPath ?: "", exposure)
            Type.SolidColor -> Mode.SolidColor(horizonColor)
        },
    )

    /** Atmospheric horizon color delegate. */
    var horizonColor: Color
        get() = when (val m = mode) {
            is Mode.Procedural -> m.horizonColor
            is Mode.SolidColor -> m.color
            is Mode.Cubemap -> DefaultHorizonColor
        }
        set(value) {
            when (val m = mode) {
                is Mode.Procedural -> m.horizonColor = value
                is Mode.SolidColor -> m.color = value
                is Mode.Cubemap -> mode = Mode.Procedural(horizonColor = value)
            }
        }

    /** Atmospheric zenith color delegate. */
    var zenithColor: Color
        get() = when (val m = mode) {
            is Mode.Procedural -> m.zenithColor
            is Mode.SolidColor -> m.color
            is Mode.Cubemap -> DefaultZenithColor
        }
        set(value) {
            when (val m = mode) {
                is Mode.Procedural -> m.zenithColor = value
                is Mode.SolidColor -> mode = Mode.Procedural(zenithColor = value)
                is Mode.Cubemap -> mode = Mode.Procedural(zenithColor = value)
            }
        }

    /** Path to cubemap asset delegate. */
    var cubemapPath: String?
        get() = (mode as? Mode.Cubemap)?.assetPath
        set(value) {
            val current = mode
            if (current is Mode.Cubemap) {
                current.assetPath = value ?: ""
            } else if (value != null) {
                mode = Mode.Cubemap(assetPath = value)
            }
        }

    /** Cubemap exposure multiplier delegate. */
    var exposure: Float
        get() = (mode as? Mode.Cubemap)?.exposure ?: 1.0f
        set(value) {
            val current = mode
            if (current is Mode.Cubemap) {
                current.exposure = value
            }
        }

    /** Skybox mode enum delegate. */
    var type: Type
        get() = when (mode) {
            is Mode.Procedural -> Type.Procedural
            is Mode.Cubemap -> Type.Cubemap
            is Mode.SolidColor -> Type.SolidColor
        }
        set(value) {
            if (type == value) return
            mode = when (value) {
                Type.Procedural -> Mode.Procedural(horizonColor, zenithColor)
                Type.Cubemap -> Mode.Cubemap(cubemapPath ?: "", exposure)
                Type.SolidColor -> Mode.SolidColor(horizonColor)
            }
        }
}
