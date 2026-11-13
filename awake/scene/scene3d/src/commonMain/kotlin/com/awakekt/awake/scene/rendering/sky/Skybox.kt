/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import com.awakekt.awake.core.color.Color

/** Default atmospheric horizon color for procedural sky gradients. */
val DefaultHorizonColor = Color(r = 0.72f, g = 0.80f, b = 0.88f, a = 1f)

/** Default atmospheric zenith color for procedural sky gradients. */
val DefaultZenithColor = Color(r = 0.20f, g = 0.38f, b = 0.68f, a = 1f)

/**
 * Skybox component — defines the atmospheric sky gradient or sky appearance behind the scene.
 *
 * @property enabled Whether procedural skybox and atmospheric horizon are rendered.
 * @property mode The active skybox rendering mode (procedural gradient, cubemap texture, or solid color).
 */
data class Skybox(
    var enabled: Boolean = true,
    var mode: Mode = Mode.Procedural(),
) {
    /** Asset path of an image drawn as the sun, or null for its disc. Not part of [copy] or equality. */
    var sunTexture: String? = null

    /** Asset path of an image drawn as the moon, or null for its disc. */
    var moonTexture: String? = null

    /** The moon's phase, 0 to 1: 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter. */
    var moonPhase: Float = FULL_MOON

    /** Whether the sun and moon are drawn over this sky whatever its type. */
    var sunAndMoon: Boolean = false

    /**
     * Whether the sun and moon are drawn over this sky by the celestial pass rather than by a
     * procedural sky's own discs: asked for, or given a texture or a phase other than full.
     */
    val drawsCelestials: Boolean
        get() = enabled && (sunAndMoon || sunTexture != null || moonTexture != null || moonPhase != FULL_MOON)

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
         * @property panoramas More panoramas by time of day; empty means the single [assetPath].
         * @property tint Colour the sky is multiplied by. Play state a `day_cycle` writes, never saved.
         * @property dayExposure Further exposure multiplier a `day_cycle` writes. Play state, never saved.
         * @property blendFrom Index into the panoramas (or 0 for [assetPath]) shown at blend 0.
         * @property blendTo Index of the panorama blended in, reaching it at [blend] 1.
         * @property blend How far [blendFrom] has moved to [blendTo], from 0 to 1. Play state, never saved.
         */
        data class Cubemap(
            var assetPath: String = "",
            var exposure: Float = 1.0f,
            var panoramas: List<ScenePanorama> = emptyList(),
            var tint: Color = Color(r = 1f, g = 1f, b = 1f, a = 1f),
            var dayExposure: Float = 1.0f,
            var blendFrom: Int = 0,
            var blendTo: Int = 0,
            var blend: Float = 0f,
        ) : Mode {
            /** The constructor as released before panoramas, kept so compiled callers still link. */
            @Deprecated("Use the constructor with panoramas.", level = DeprecationLevel.HIDDEN)
            constructor(assetPath: String = "", exposure: Float = 1.0f) : this(assetPath, exposure, emptyList())

            /** [copy] as released before panoramas. */
            @Deprecated("Use copy with panoramas.", level = DeprecationLevel.HIDDEN)
            fun copy(assetPath: String = this.assetPath, exposure: Float = this.exposure): Cubemap =
                copy(assetPath, exposure, panoramas, tint, dayExposure, blendFrom, blendTo, blend)
        }

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
        /** Procedural two-color atmospheric gradient. */
        Procedural,

        /** Sampled 6-face environment cubemap. */
        Cubemap,

        /** Single uniform solid background clear color. */
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
