/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.fog

import com.awakekt.awake.scene.binding.SavedOnly
import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.binding.SceneBindingHooks
import com.awakekt.awake.scene.binding.SceneColorConverter
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.rendering.light.legacyColor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --8<-- [start:fog]
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
@SceneBinding(Fog::class, converters = [SceneColorConverter::class], hooks = LegacyFogColor::class)
data class SceneFog(
    val enabled: Boolean = true,
    val density: Float = 0.001f,
    val color: SceneColor = SceneColor(0.55f, 0.62f, 0.70f, 1f),
    @SavedOnly val colorR: Float? = null,
    @SavedOnly val colorG: Float? = null,
    @SavedOnly val colorB: Float? = null,
) : SceneComponent

/** Loads the separate [SceneFog.colorR], `colorG` and `colorB` channels an older document wrote over its color. */
internal object LegacyFogColor : SceneBindingHooks<Fog, SceneFog> {
    override fun afterCopyToLive(saved: SceneFog, live: Fog): Fog {
        legacyColor(saved.colorR, saved.colorG, saved.colorB)?.let { live.color = it }
        return live
    }
}
// --8<-- [end:fog]
