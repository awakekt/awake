/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuEnvironmentState

/**
 * Per-frame environmental uniforms passed to the renderer for sky, fog, and global shadows.
 *
 * This represents the hardware/GPU render contract for environment shading (pure shader uniforms),
 * strictly decoupled from any ECS scene-graph or authoring components.
 */
data class EnvironmentUniforms(
    /** Whether the procedural sky should be drawn. */
    val showSky: Boolean = false,
    /** Clear sky color at the horizon. */
    val horizonColor: Color = DEFAULT_HORIZON_COLOR,
    /** Clear sky color at the zenith (top of the sky hemisphere). */
    val zenithColor: Color = DEFAULT_ZENITH_COLOR,
    /** Atmospheric exponential fog density factor (0 = no fog). */
    val fogDensity: Float = 0f,
    /** Atmospheric fog color. */
    val fogColor: Color = DEFAULT_FOG_COLOR,
    /** Whether directional shadow cascades are rendered. */
    val shadowsEnabled: Boolean = true,
    /** What the scene shaders draw instead of their lit colour. */
    val debugView: RenderDebugView = RenderDebugView.Off,
    /** The shadow-map layer [RenderDebugView.ShadowMap] shows. */
    val debugLayer: Int = 0,
    /**
     * What the lit shaders multiply their radiance by before tone mapping. At 1 a white surface
     * facing a light of intensity 1 shows near white; 2 is one stop brighter.
     */
    val exposure: Float = 1f,
    /**
     * Whether the plan's content features (sky, terrain, fog and the like) draw. False draws only
     * the pass's own draws: an asset preview, which must not show the scene around it.
     */
    val contentFeatures: Boolean = true,
    /** The colour the pass clears to; null keeps the renderer's own. */
    val clearColor: Color? = null,
    /**
     * Whether the opaque draws' triangle edges are drawn over the frame, lit or under [debugView]:
     * a skinned mesh's edges deform with it. Draws whose shader shows no debug views, and instanced
     * draws, draw no edges.
     */
    val wireframe: Boolean = false,
    /** Whether the built-in sky draws its own sun and moon discs; false when a scene draws them over the sky. */
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
    val surfaceDepositSlope: Float = GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_SLOPE,
    /** Edge feathering and organic breakup width for surface coating transitions. */
    val surfaceDepositSoftness: Float = GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_SOFTNESS,
    /** Surface roughness applied to coated PBR fragments (`0` glossy wet, `1` matte powder). */
    val surfaceDepositRoughness: Float = GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_ROUGHNESS,
    /** World-space frequency for procedural organic breakup of surface coating. */
    val surfaceDepositNoiseScale: Float = GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_NOISE_SCALE,
    /** Optional crevice/hollow contrast colour (`null` auto-derives a darker shaded tone). */
    val surfaceDepositSecondaryColor: Color? = null,
    /** Micro-sparkle glint intensity in `[0, 1]` (`0` for matte ash/dust, positive for snow/frost). */
    val surfaceDepositGlint: Float = 0f,
    /** Procedural micro-texture and micro-bump grain contrast in `[0, 1]`. */
    val surfaceDepositGrain: Float = GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_GRAIN,
    /** Per-storm spatial randomization seed shifting drift placement and wind-stretch angle. */
    val surfaceDepositSeed: Float = 0f,
) {
    /** The constructor as released before [surfaceDepositColor], kept so compiled callers still link. */
    @Deprecated("Use the constructor with surfaceDeposit fields.", level = DeprecationLevel.HIDDEN)
    constructor(
        showSky: Boolean = false,
        horizonColor: Color = DEFAULT_HORIZON_COLOR,
        zenithColor: Color = DEFAULT_ZENITH_COLOR,
        fogDensity: Float = 0f,
        fogColor: Color = DEFAULT_FOG_COLOR,
        shadowsEnabled: Boolean = true,
        debugView: RenderDebugView = RenderDebugView.Off,
        debugLayer: Int = 0,
        exposure: Float = 1f,
        contentFeatures: Boolean = true,
        clearColor: Color? = null,
        wireframe: Boolean = false,
        skyBodies: Boolean = true,
        fogStart: Float = 0f,
        fogEnd: Float = 0f,
    ) : this(
        showSky, horizonColor, zenithColor, fogDensity, fogColor, shadowsEnabled, debugView, debugLayer, exposure,
        contentFeatures, clearColor, wireframe, skyBodies, fogStart, fogEnd, Color.White, 0f,
        GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_SLOPE, GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_SOFTNESS,
        GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_ROUGHNESS, GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_NOISE_SCALE,
        null, 0f, GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_GRAIN, 0f,
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
        debugView: RenderDebugView = this.debugView,
        debugLayer: Int = this.debugLayer,
        exposure: Float = this.exposure,
        contentFeatures: Boolean = this.contentFeatures,
        clearColor: Color? = this.clearColor,
        wireframe: Boolean = this.wireframe,
        skyBodies: Boolean = this.skyBodies,
        fogStart: Float = this.fogStart,
        fogEnd: Float = this.fogEnd,
    ): EnvironmentUniforms = copy(
        showSky, horizonColor, zenithColor, fogDensity, fogColor, shadowsEnabled, debugView, debugLayer, exposure,
        contentFeatures, clearColor, wireframe, skyBodies, fogStart, fogEnd, surfaceDepositColor, surfaceDepositAmount,
        surfaceDepositSlope, surfaceDepositSoftness, surfaceDepositRoughness, surfaceDepositNoiseScale,
        surfaceDepositSecondaryColor, surfaceDepositGlint, surfaceDepositGrain, surfaceDepositSeed,
    )

    /** The constructor as released before [fogStart], kept so compiled callers still link. */
    @Deprecated("Use the constructor with fogStart and fogEnd.", level = DeprecationLevel.HIDDEN)
    constructor(
        showSky: Boolean = false,
        horizonColor: Color = DEFAULT_HORIZON_COLOR,
        zenithColor: Color = DEFAULT_ZENITH_COLOR,
        fogDensity: Float = 0f,
        fogColor: Color = DEFAULT_FOG_COLOR,
        shadowsEnabled: Boolean = true,
        debugView: RenderDebugView = RenderDebugView.Off,
        debugLayer: Int = 0,
        exposure: Float = 1f,
        contentFeatures: Boolean = true,
        clearColor: Color? = null,
        wireframe: Boolean = false,
        skyBodies: Boolean = true,
    ) : this(
        showSky, horizonColor, zenithColor, fogDensity, fogColor, shadowsEnabled, debugView, debugLayer, exposure,
        contentFeatures, clearColor, wireframe, skyBodies, 0f, 0f, Color.White, 0f,
        GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_SLOPE, GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_SOFTNESS,
        GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_ROUGHNESS, GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_NOISE_SCALE,
        null, 0f, GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_GRAIN, 0f,
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
        debugView: RenderDebugView = this.debugView,
        debugLayer: Int = this.debugLayer,
        exposure: Float = this.exposure,
        contentFeatures: Boolean = this.contentFeatures,
        clearColor: Color? = this.clearColor,
        wireframe: Boolean = this.wireframe,
        skyBodies: Boolean = this.skyBodies,
    ): EnvironmentUniforms = copy(
        showSky, horizonColor, zenithColor, fogDensity, fogColor, shadowsEnabled, debugView, debugLayer, exposure,
        contentFeatures, clearColor, wireframe, skyBodies, fogStart, fogEnd, surfaceDepositColor, surfaceDepositAmount,
        surfaceDepositSlope, surfaceDepositSoftness, surfaceDepositRoughness, surfaceDepositNoiseScale,
        surfaceDepositSecondaryColor, surfaceDepositGlint, surfaceDepositGrain, surfaceDepositSeed,
    )

    /** The constructor as released before [skyBodies], kept so compiled callers still link. */
    @Deprecated("Use the constructor with skyBodies.", level = DeprecationLevel.HIDDEN)
    constructor(
        showSky: Boolean = false,
        horizonColor: Color = DEFAULT_HORIZON_COLOR,
        zenithColor: Color = DEFAULT_ZENITH_COLOR,
        fogDensity: Float = 0f,
        fogColor: Color = DEFAULT_FOG_COLOR,
        shadowsEnabled: Boolean = true,
        debugView: RenderDebugView = RenderDebugView.Off,
        debugLayer: Int = 0,
        exposure: Float = 1f,
        contentFeatures: Boolean = true,
        clearColor: Color? = null,
        wireframe: Boolean = false,
    ) : this(
        showSky, horizonColor, zenithColor, fogDensity, fogColor, shadowsEnabled, debugView, debugLayer, exposure,
        contentFeatures, clearColor, wireframe, true, 0f, 0f, Color.White, 0f,
        GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_SLOPE, GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_SOFTNESS,
        GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_ROUGHNESS, GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_NOISE_SCALE,
        null, 0f, GpuEnvironmentState.DEFAULT_SURFACE_DEPOSIT_GRAIN, 0f,
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
        debugView: RenderDebugView = this.debugView,
        debugLayer: Int = this.debugLayer,
        exposure: Float = this.exposure,
        contentFeatures: Boolean = this.contentFeatures,
        clearColor: Color? = this.clearColor,
        wireframe: Boolean = this.wireframe,
    ): EnvironmentUniforms = copy(
        showSky, horizonColor, zenithColor, fogDensity, fogColor, shadowsEnabled, debugView, debugLayer, exposure,
        contentFeatures, clearColor, wireframe, skyBodies, fogStart, fogEnd, surfaceDepositColor, surfaceDepositAmount,
        surfaceDepositSlope, surfaceDepositSoftness, surfaceDepositRoughness, surfaceDepositNoiseScale,
        surfaceDepositSecondaryColor, surfaceDepositGlint, surfaceDepositGrain, surfaceDepositSeed,
    )

    init {
        require(exposure > 0f && exposure.isFinite()) { "exposure must be finite and above 0; was $exposure." }
    }

    /** Companion object containing default environment uniform instances. */
    companion object {
        /** Default environment uniform configuration. */
        val Default = EnvironmentUniforms()
    }

    /**
     * Lowers this to the pass's hardware-ready state. A debug view hides the sky, which would
     * otherwise read as data behind it.
     *
     * @param viewDepthRange The camera's far distance: what [RenderDebugView.LinearDepth] shows
     *   as white.
     */
    fun toGpuState(viewDepthRange: Float): GpuEnvironmentState = GpuEnvironmentState(
        showSky = showSky && debugView == RenderDebugView.Off,
        horizonColor = horizonColor,
        zenithColor = zenithColor,
        fogDensity = fogDensity,
        fogColor = fogColor,
        shadowsEnabled = shadowsEnabled,
        debugView = if (debugView == RenderDebugView.Off) {
            GpuDebugView.Off
        } else {
            GpuDebugView(debugView.code, viewDepthRange, debugLayer)
        },
        exposure = exposure,
        contentFeatures = contentFeatures,
        clearColor = clearColor,
        skyBodies = skyBodies,
        fogStart = fogStart,
        fogEnd = fogEnd,
        surfaceDepositColor = surfaceDepositColor,
        surfaceDepositAmount = surfaceDepositAmount,
        surfaceDepositSlope = surfaceDepositSlope,
        surfaceDepositSoftness = surfaceDepositSoftness,
        surfaceDepositRoughness = surfaceDepositRoughness,
        surfaceDepositNoiseScale = surfaceDepositNoiseScale,
        surfaceDepositSecondaryColor = surfaceDepositSecondaryColor,
        surfaceDepositGlint = surfaceDepositGlint,
        surfaceDepositGrain = surfaceDepositGrain,
        surfaceDepositSeed = surfaceDepositSeed,
    )
}
