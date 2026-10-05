/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.fog

import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable distance fog component for scene documents.
 *
 * @property enabled Whether distance fog shading is active.
 * @property density Distance fog density factor.
 * @property color Base distance fog color.
 * @property colorR Optional explicit red channel color override.
 * @property colorG Optional explicit green channel color override.
 * @property colorB Optional explicit blue channel color override.
 */
@Serializable
@SerialName("fog")
data class SceneFog(
    val enabled: Boolean = true,
    val density: Float = 0.001f,
    val color: SceneColor = SceneColor(0.55f, 0.62f, 0.70f, 1f),
    val colorR: Float? = null,
    val colorG: Float? = null,
    val colorB: Float? = null,
) : SceneComponent
