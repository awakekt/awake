/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.fog

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.binding.SavedOnly
import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.binding.SceneBindingHooks
import com.awakekt.awake.scene.binding.SceneColorConverter
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
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
 * @property mode How fog grows with distance; a document without it fogs exponentially, as before.
 * @property start Where a linear fog starts, in metres from the eye.
 * @property end Where a linear fog reaches [color], in metres from the eye; beyond [start].
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
    val mode: FogMode = FogMode.Exponential,
    @PropertyRange(min = 0.0) val start: Float = DEFAULT_FOG_START,
    val end: Float = DEFAULT_FOG_END,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> =
        fogRangeProblem(start, end)?.let { listOf(SceneValidationIssue(path, "fog.$it")) }.orEmpty()

    /** The constructor as released before [mode], kept so compiled callers still link. */
    @Deprecated("Use the constructor with mode, start and end.", level = DeprecationLevel.HIDDEN)
    constructor(
        enabled: Boolean = true,
        density: Float = 0.001f,
        color: SceneColor = SceneColor(0.55f, 0.62f, 0.70f, 1f),
        colorR: Float? = null,
        colorG: Float? = null,
        colorB: Float? = null,
    ) : this(enabled, density, color, colorR, colorG, colorB, FogMode.Exponential, DEFAULT_FOG_START, DEFAULT_FOG_END)

    /** [copy] as released before [mode]. */
    @Deprecated("Use copy with mode, start and end.", level = DeprecationLevel.HIDDEN)
    fun copy(
        enabled: Boolean = this.enabled,
        density: Float = this.density,
        color: SceneColor = this.color,
        colorR: Float? = this.colorR,
        colorG: Float? = this.colorG,
        colorB: Float? = this.colorB,
    ): SceneFog = copy(enabled, density, color, colorR, colorG, colorB, mode, start, end)
}

/** Loads the separate [SceneFog.colorR], `colorG` and `colorB` channels an older document wrote over its color. */
internal object LegacyFogColor : SceneBindingHooks<Fog, SceneFog> {
    override fun afterCopyToLive(saved: SceneFog, live: Fog): Fog {
        legacyColor(saved.colorR, saved.colorG, saved.colorB)?.let { live.color = it }
        return live
    }
}
// --8<-- [end:fog]
