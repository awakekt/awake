/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable ambient light component for scene documents.
 */
@Serializable
@SerialName("ambient_light")
data class SceneAmbientLight(
    val intensity: Float = 0.2f,
    val color: SceneColor = SceneColor.White,
    val colorR: Float? = null,
    val colorG: Float? = null,
    val colorB: Float? = null,
) : SceneComponent
