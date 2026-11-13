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
) {
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
        clearColor, true,
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
        clearColor, skyBodies,
    )

    /** The colour this pass clears to: [clearColor] when it sets one, else [default]. */
    fun clearColorOr(default: Color): Color = clearColor ?: default

    /** Predefined environment state constants. */
    companion object {
        /** Default environment state with sky disabled, no fog, and shadows enabled. */
        val Default = GpuEnvironmentState()
    }
}
