/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable skybox component for scene documents.
 *
 * @property enabled Whether skybox rendering is enabled.
 * @property horizonColor Atmospheric horizon gradient color.
 * @property zenithColor Atmospheric zenith gradient color.
 * @property horizonColorR Optional explicit red channel override for the horizon color.
 * @property horizonColorG Optional explicit green channel override for the horizon color.
 * @property horizonColorB Optional explicit blue channel override for the horizon color.
 * @property zenithColorR Optional explicit red channel override for the zenith color.
 * @property zenithColorG Optional explicit green channel override for the zenith color.
 * @property zenithColorB Optional explicit blue channel override for the zenith color.
 * @property cubemapPath Asset path to cubemap texture when mode is cubemap.
 * @property exposure Exposure multiplier applied during cubemap rendering.
 * @property type Skybox mode type name ("Procedural", "Cubemap", or "SolidColor").
 * @property panoramas More cubemap panoramas by time of day, in time order. A `day_cycle` blends the
 *   two around its time; without one the first shows. Empty keeps the single [cubemapPath].
 * @property sunTexture Optional asset path of an image drawn as the sun, on a square the size of its disc.
 * @property moonTexture Optional asset path of an image drawn as the moon.
 * @property moonPhase The moon's phase from 0 to 1: 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter.
 * @property sunAndMoon Draw the sun and moon over this sky, whatever its type, instead of only a
 *   procedural sky's own discs. A texture or a phase other than full draws them too.
 * @property additiveBodies Add the sun and moon to the sky instead of covering it, as a glowing sun
 *   texture wants.
 */
@Serializable
@SerialName("skybox")
data class SceneSkybox(
    val enabled: Boolean = true,
    val horizonColor: SceneColor = SceneColor(0.72f, 0.80f, 0.88f, 1f),
    val zenithColor: SceneColor = SceneColor(0.20f, 0.38f, 0.68f, 1f),
    val horizonColorR: Float? = null,
    val horizonColorG: Float? = null,
    val horizonColorB: Float? = null,
    val zenithColorR: Float? = null,
    val zenithColorG: Float? = null,
    val zenithColorB: Float? = null,
    val cubemapPath: String? = null,
    val exposure: Float = 1.0f,
    val type: String = "Procedural",
    val panoramas: List<ScenePanorama> = emptyList(),
    val sunTexture: String? = null,
    val moonTexture: String? = null,
    @PropertyRange(min = 0.0, max = 1.0) val moonPhase: Float = FULL_MOON,
    val sunAndMoon: Boolean = false,
    val additiveBodies: Boolean = false,
) : SceneComponent {
    /** The constructor as released before panoramas, kept so compiled callers still link. */
    @Deprecated("Use the constructor with panoramas.", level = DeprecationLevel.HIDDEN)
    constructor(
        enabled: Boolean = true,
        horizonColor: SceneColor = SceneColor(0.72f, 0.80f, 0.88f, 1f),
        zenithColor: SceneColor = SceneColor(0.20f, 0.38f, 0.68f, 1f),
        horizonColorR: Float? = null,
        horizonColorG: Float? = null,
        horizonColorB: Float? = null,
        zenithColorR: Float? = null,
        zenithColorG: Float? = null,
        zenithColorB: Float? = null,
        cubemapPath: String? = null,
        exposure: Float = 1.0f,
        type: String = "Procedural",
    ) : this(
        enabled, horizonColor, zenithColor, horizonColorR, horizonColorG, horizonColorB, zenithColorR, zenithColorG,
        zenithColorB, cubemapPath, exposure, type, emptyList(),
    )

    /** [copy] as released before panoramas. */
    @Deprecated("Use copy with panoramas.", level = DeprecationLevel.HIDDEN)
    fun copy(
        enabled: Boolean = this.enabled,
        horizonColor: SceneColor = this.horizonColor,
        zenithColor: SceneColor = this.zenithColor,
        horizonColorR: Float? = this.horizonColorR,
        horizonColorG: Float? = this.horizonColorG,
        horizonColorB: Float? = this.horizonColorB,
        zenithColorR: Float? = this.zenithColorR,
        zenithColorG: Float? = this.zenithColorG,
        zenithColorB: Float? = this.zenithColorB,
        cubemapPath: String? = this.cubemapPath,
        exposure: Float = this.exposure,
        type: String = this.type,
    ): SceneSkybox = copy(
        enabled, horizonColor, zenithColor, horizonColorR, horizonColorG, horizonColorB, zenithColorR, zenithColorG,
        zenithColorB, cubemapPath, exposure, type, panoramas,
    )

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        val times = panoramas.map { it.time }
        if (times.any { !(it >= 0f && it < 1f) } || times.zipWithNext().any { (a, b) -> b < a }) {
            add(SceneValidationIssue(path, "skybox.panoramas must be in time order from 0 up to 1"))
        }
        if (!(moonPhase >= 0f && moonPhase <= 1f)) add(SceneValidationIssue(path, "skybox.moonPhase must be from 0 to 1; was $moonPhase"))
    }
}

/** A full moon: the whole disc, as the procedural sky draws it. */
const val FULL_MOON = 0.5f
