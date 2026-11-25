/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.color.Color

/**
 * Backend-neutral feature state carried by a compiled GPU pass.
 *
 * Scene/game authoring code never crosses the backend boundary. The scene/render compiler lowers
 * its richer model to this small value before submitting the packet to the hardware contract.
 * Backends consume this packet; they do not discover or mutate scene environment components.
 */
data class GpuEnvironmentState(
    /** Whether procedural background sky rendering is enabled. */
    val showSky: Boolean = false,
    /** Horizon color used for procedural sky gradient. */
    val horizonColor: Color = Color.Black,
    /** Zenith color used for procedural sky gradient. */
    val zenithColor: Color = Color.Black,
    /** Fog density coefficient controlling distance fog falloff. */
    val fogDensity: Float = 0f,
    /** Color of the atmospheric distance fog. */
    val fogColor: Color = Color.Black,
    /** Whether directional shadow mapping is evaluated in lit shaders. */
    val shadowsEnabled: Boolean = true,
    /** A diagnostic that replaces the scene shaders' lit output; [GpuDebugView.Off] renders normally. */
    val debugView: GpuDebugView = GpuDebugView.Off,
    /** What the lit shaders multiply their radiance by before tone mapping. */
    val exposure: Float = 1f,
    /**
     * Whether the plan's content features (sky, terrain, fog and the like) draw in this pass.
     * False draws only the pass's own draws, as an asset preview needs.
     */
    val contentFeatures: Boolean = true,
    /** The colour this pass clears to; null keeps the renderer's own `clearColor`. */
    val clearColor: Color? = null,
    /**
     * Whether the built-in sky draws its own sun and moon discs. False when a scene draws them
     * itself, over any sky, so they are not drawn twice.
     */
    val skyBodies: Boolean = true,
    /**
     * Where linear fog starts, in metres from the eye. Fog is linear, from here to [fogEnd], only
     * while [fogEnd] is beyond it; otherwise it is exponential by [fogDensity], as before (#1807).
     */
    val fogStart: Float = 0f,
    /** Where linear fog reaches [fogColor], in metres from the eye; see [fogStart]. */
    val fogEnd: Float = 0f,
    /** Primary highlight/crest colour of dynamic upward-facing surface coating (e.g. snow, ash, dust). */
    val surfaceDepositColor: Color = Color.White,
    /** Dynamic surface coating coverage in `[0, 1]`; `0` disables surface deposit shading. */
    val surfaceDepositAmount: Float = 0f,
    /** Minimum world-up normal component `dot(N, +Y)` where surface coating settles. */
    val surfaceDepositSlope: Float = DEFAULT_SURFACE_DEPOSIT_SLOPE,
    /** Edge feathering and organic breakup width for surface coating transitions. */
    val surfaceDepositSoftness: Float = DEFAULT_SURFACE_DEPOSIT_SOFTNESS,
    /** Surface roughness applied to coated PBR fragments (`0` glossy wet, `1` matte powder). */
    val surfaceDepositRoughness: Float = DEFAULT_SURFACE_DEPOSIT_ROUGHNESS,
    /** World-space frequency for procedural organic breakup of surface coating. */
    val surfaceDepositNoiseScale: Float = DEFAULT_SURFACE_DEPOSIT_NOISE_SCALE,
    /** Optional crevice/hollow contrast colour (`null` auto-derives a cool darker shade from [surfaceDepositColor]). */
    val surfaceDepositSecondaryColor: Color? = null,
    /** Micro-sparkle glint intensity in `[0, 1]` (`0` for matte ash/dust, positive for snow/frost). */
    val surfaceDepositGlint: Float = 0f,
    /** Procedural micro-texture and micro-bump grain contrast in `[0, 1]`. */
    val surfaceDepositGrain: Float = DEFAULT_SURFACE_DEPOSIT_GRAIN,
    /** Per-storm spatial randomization seed shifting drift placement and wind-stretch angle. */
    val surfaceDepositSeed: Float = 0f,
) {
    /** The constructor as released before [surfaceDepositColor], kept so compiled callers still link. */
    @Deprecated("Use the constructor with surfaceDeposit fields.", level = DeprecationLevel.HIDDEN)
    constructor(
        showSky: Boolean = false,
        horizonColor: Color = Color.Black,
        zenithColor: Color = Color.Black,
        fogDensity: Float = 0f,
        fogColor: Color = Color.Black,
        shadowsEnabled: Boolean = true,
        debugView: GpuDebugView = GpuDebugView.Off,
        exposure: Float = 1f,
        contentFeatures: Boolean = true,
        clearColor: Color? = null,
        skyBodies: Boolean = true,
        fogStart: Float = 0f,
        fogEnd: Float = 0f,
    ) : this(
        showSky, horizonColor, zenithColor, fogDensity, fogColor, shadowsEnabled, debugView, exposure, contentFeatures,
        clearColor, skyBodies, fogStart, fogEnd, Color.White, 0f, DEFAULT_SURFACE_DEPOSIT_SLOPE,
        DEFAULT_SURFACE_DEPOSIT_SOFTNESS, DEFAULT_SURFACE_DEPOSIT_ROUGHNESS, DEFAULT_SURFACE_DEPOSIT_NOISE_SCALE,
        null, 0f, DEFAULT_SURFACE_DEPOSIT_GRAIN, 0f,
    )

    /** [copy] as released before [surfaceDepositColor]. */
    @Deprecated("Use copy with surfaceDeposit fields.", level = DeprecationLevel.HIDDEN)
    fun copy(
        showSky: Boolean = this.showSky,
        horizonColor: Color = this.horizonColor,
        zenithColor: Color = this.zenithColor,
        fogDensity: Float = this.fogDensity,
        fogColor: Color = this.fogColor,
        shadowsEnabled: Boolean = this.shadowsEnabled,
        debugView: GpuDebugView = this.debugView,
        exposure: Float = this.exposure,
        contentFeatures: Boolean = this.contentFeatures,
        clearColor: Color? = this.clearColor,
        skyBodies: Boolean = this.skyBodies,
        fogStart: Float = this.fogStart,
        fogEnd: Float = this.fogEnd,
    ): GpuEnvironmentState = copy(
        showSky, horizonColor, zenithColor, fogDensity, fogColor, shadowsEnabled, debugView, exposure, contentFeatures,
        clearColor, skyBodies, fogStart, fogEnd, surfaceDepositColor, surfaceDepositAmount, surfaceDepositSlope,
        surfaceDepositSoftness, surfaceDepositRoughness, surfaceDepositNoiseScale, surfaceDepositSecondaryColor,
        surfaceDepositGlint, surfaceDepositGrain, surfaceDepositSeed,
    )

    /** The constructor as released before [fogStart], kept so compiled callers still link. */
    @Deprecated("Use the constructor with fogStart and fogEnd.", level = DeprecationLevel.HIDDEN)
    constructor(
        showSky: Boolean = false,
        horizonColor: Color = Color.Black,
        zenithColor: Color = Color.Black,
        fogDensity: Float = 0f,
        fogColor: Color = Color.Black,
        shadowsEnabled: Boolean = true,
        debugView: GpuDebugView = GpuDebugView.Off,
        exposure: Float = 1f,
        contentFeatures: Boolean = true,
        clearColor: Color? = null,
        skyBodies: Boolean = true,
    ) : this(
        showSky, horizonColor, zenithColor, fogDensity, fogColor, shadowsEnabled, debugView, exposure, contentFeatures,
        clearColor, skyBodies, 0f, 0f, Color.White, 0f, DEFAULT_SURFACE_DEPOSIT_SLOPE,
        DEFAULT_SURFACE_DEPOSIT_SOFTNESS, DEFAULT_SURFACE_DEPOSIT_ROUGHNESS, DEFAULT_SURFACE_DEPOSIT_NOISE_SCALE,
        null, 0f, DEFAULT_SURFACE_DEPOSIT_GRAIN, 0f,
    )

    /** [copy] as released before [fogStart]. */
    @Deprecated("Use copy with fogStart and fogEnd.", level = DeprecationLevel.HIDDEN)
    fun copy(
        showSky: Boolean = this.showSky,
        horizonColor: Color = this.horizonColor,
        zenithColor: Color = this.zenithColor,
        fogDensity: Float = this.fogDensity,
        fogColor: Color = this.fogColor,
        shadowsEnabled: Boolean = this.shadowsEnabled,
        debugView: GpuDebugView = this.debugView,
        exposure: Float = this.exposure,
        contentFeatures: Boolean = this.contentFeatures,
        clearColor: Color? = this.clearColor,
        skyBodies: Boolean = this.skyBodies,
    ): GpuEnvironmentState = copy(
        showSky, horizonColor, zenithColor, fogDensity, fogColor, shadowsEnabled, debugView, exposure, contentFeatures,
        clearColor, skyBodies, fogStart, fogEnd, surfaceDepositColor, surfaceDepositAmount, surfaceDepositSlope,
        surfaceDepositSoftness, surfaceDepositRoughness, surfaceDepositNoiseScale, surfaceDepositSecondaryColor,
        surfaceDepositGlint, surfaceDepositGrain, surfaceDepositSeed,
    )

    /** The constructor as released before [skyBodies], kept so compiled callers still link. */
    @Deprecated("Use the constructor with skyBodies.", level = DeprecationLevel.HIDDEN)
    constructor(
        showSky: Boolean = false,
        horizonColor: Color = Color.Black,
        zenithColor: Color = Color.Black,
        fogDensity: Float = 0f,
        fogColor: Color = Color.Black,
        shadowsEnabled: Boolean = true,
        debugView: GpuDebugView = GpuDebugView.Off,
        exposure: Float = 1f,
        contentFeatures: Boolean = true,
        clearColor: Color? = null,
    ) : this(
        showSky, horizonColor, zenithColor, fogDensity, fogColor, shadowsEnabled, debugView, exposure, contentFeatures,
        clearColor, true, 0f, 0f, Color.White, 0f, DEFAULT_SURFACE_DEPOSIT_SLOPE,
        DEFAULT_SURFACE_DEPOSIT_SOFTNESS, DEFAULT_SURFACE_DEPOSIT_ROUGHNESS, DEFAULT_SURFACE_DEPOSIT_NOISE_SCALE,
        null, 0f, DEFAULT_SURFACE_DEPOSIT_GRAIN, 0f,
    )

    /** [copy] as released before [skyBodies]. */
    @Deprecated("Use copy with skyBodies.", level = DeprecationLevel.HIDDEN)
    fun copy(
        showSky: Boolean = this.showSky,
        horizonColor: Color = this.horizonColor,
        zenithColor: Color = this.zenithColor,
        fogDensity: Float = this.fogDensity,
        fogColor: Color = this.fogColor,
        shadowsEnabled: Boolean = this.shadowsEnabled,
        debugView: GpuDebugView = this.debugView,
        exposure: Float = this.exposure,
        contentFeatures: Boolean = this.contentFeatures,
        clearColor: Color? = this.clearColor,
    ): GpuEnvironmentState = copy(
        showSky, horizonColor, zenithColor, fogDensity, fogColor, shadowsEnabled, debugView, exposure, contentFeatures,
        clearColor, skyBodies, fogStart, fogEnd, surfaceDepositColor, surfaceDepositAmount, surfaceDepositSlope,
        surfaceDepositSoftness, surfaceDepositRoughness, surfaceDepositNoiseScale, surfaceDepositSecondaryColor,
        surfaceDepositGlint, surfaceDepositGrain, surfaceDepositSeed,
    )

    /** Whether this pass fogs linearly, from [fogStart] to [fogEnd]. */
    val linearFog: Boolean get() = fogEnd > fogStart

    /** The colour this pass clears to: [clearColor] when it sets one, else [default]. */
    fun clearColorOr(default: Color): Color = clearColor ?: default

    /** Predefined environment state constants. */
    companion object {
        /** Default upward-normal threshold for dynamic surface coating. */
        const val DEFAULT_SURFACE_DEPOSIT_SLOPE: Float = 0.52f

        /** Default transition softness and organic breakup width for dynamic surface coating. */
        const val DEFAULT_SURFACE_DEPOSIT_SOFTNESS: Float = 0.24f

        /** Default PBR roughness applied where surface coating accumulates. */
        const val DEFAULT_SURFACE_DEPOSIT_ROUGHNESS: Float = 0.92f

        /** Default world-space organic noise frequency for dynamic surface coating. */
        const val DEFAULT_SURFACE_DEPOSIT_NOISE_SCALE: Float = 1.6f

        /** Default procedural micro-texture and micro-bump grain strength. */
        const val DEFAULT_SURFACE_DEPOSIT_GRAIN: Float = 0.55f

        /** Default environment state with sky disabled, no fog, and shadows enabled. */
        val Default = GpuEnvironmentState()
    }
}
