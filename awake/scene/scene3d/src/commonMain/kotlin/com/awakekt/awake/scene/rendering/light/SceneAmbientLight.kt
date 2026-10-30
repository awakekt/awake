/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.scene.binding.SavedOnly
import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.binding.SceneBindingHooks
import com.awakekt.awake.scene.binding.SceneColorConverter
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable ambient light component for scene documents.
 *
 * @property intensity Ambient light intensity factor.
 * @property color Base ambient light color.
 * @property colorR Optional explicit red color channel override.
 * @property colorG Optional explicit green color channel override.
 * @property colorB Optional explicit blue color channel override.
 */
@Serializable
@SerialName("ambient_light")
@SceneBinding(AmbientLight::class, converters = [SceneColorConverter::class], hooks = LegacyAmbientColor::class)
data class SceneAmbientLight(
    val intensity: Float = 0.2f,
    val color: SceneColor = SceneColor.White,
    @SavedOnly val colorR: Float? = null,
    @SavedOnly val colorG: Float? = null,
    @SavedOnly val colorB: Float? = null,
) : SceneComponent

/** Loads the separate [SceneAmbientLight.colorR], `colorG` and `colorB` channels an older document wrote over its color. */
internal object LegacyAmbientColor : SceneBindingHooks<AmbientLight, SceneAmbientLight> {
    override fun afterCopyToLive(saved: SceneAmbientLight, live: AmbientLight): AmbientLight {
        legacyColor(saved.colorR, saved.colorG, saved.colorB)?.let { live.color = it }
        return live
    }
}

/** The opaque color an older fog or ambient light saved as three channels, when it saved all three. */
internal fun legacyColor(r: Float?, g: Float?, b: Float?): Color? =
    if (r != null && g != null && b != null) Color(r, g, b, 1f) else null
