/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
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
) : SceneComponent
