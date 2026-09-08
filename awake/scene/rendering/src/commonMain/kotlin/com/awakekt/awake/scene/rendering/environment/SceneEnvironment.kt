/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.environment

import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable environment and atmospheric sky component for scene documents.
 */
@Serializable
@SerialName("environment")
data class SceneEnvironment(
    val sunAzimuthDeg: Float = 45f,
    val sunElevationDeg: Float = 60f,
    val sunIntensity: Float = 1.0f,
    val sunColorR: Float = 1f,
    val sunColorG: Float = 0.98f,
    val sunColorB: Float = 0.95f,
    val showEnvironment: Boolean = true,
    val horizonColorR: Float = 0.72f,
    val horizonColorG: Float = 0.80f,
    val horizonColorB: Float = 0.88f,
    val zenithColorR: Float = 0.20f,
    val zenithColorG: Float = 0.38f,
    val zenithColorB: Float = 0.68f,
    val fogDensity: Float = 0.001f,
    val fogColorR: Float = 0.55f,
    val fogColorG: Float = 0.62f,
    val fogColorB: Float = 0.70f,
    val ambientLight: Float = 0.2f,
    val shadowsEnabled: Boolean = true,
) : SceneComponent
