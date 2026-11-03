/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Authored settings for a locally predicted root transform. Thresholds have the same units and
 * meaning as net:prediction's ReconcileConfig. Session state and game rules are code-only.
 *
 * @property correctMetres Drift threshold for shifting rather than blending.
 * @property farMetres Drift threshold for snapping rather than shifting.
 * @property trustedGuessedTicks Maximum server-extrapolated ticks trusted for a correction.
 * @property serverGuessWindowSeconds How long the server extrapolates before stopping.
 * @property capEaseSeconds Duration of the slowdown before the prediction cap.
 * @property predictionCapSeconds Maximum unheard-input age before stopping prediction.
 * @property planar Whether drift ignores height.
 * @property historyCapacity Number of input poses retained.
 * @property glideHalfLifeSeconds Half-life of a shift's visual offset.
 * @property blendShare Maximum share of each movement used to repay drift.
 *
 * A null [predictionCapSeconds] follows [serverGuessWindowSeconds] plus [capEaseSeconds].
 */
@Serializable
@SerialName("predicted_entity")
data class ScenePredictedEntity(
    @PropertyRange(min = 0.0) val correctMetres: Float = 1.5f,
    @PropertyRange(min = 0.0) val farMetres: Float = 10f,
    @PropertyRange(min = 0.0) val trustedGuessedTicks: Int = 2,
    @PropertyRange(min = 0.0) val serverGuessWindowSeconds: Double = 0.5,
    @PropertyRange(min = 0.0) val capEaseSeconds: Double = 0.25,
    @PropertyRange(min = 0.0, exclusiveMin = true) val predictionCapSeconds: Double? = null,
    val planar: Boolean = false,
    @PropertyRange(min = 1.0) val historyCapacity: Int = 128,
    @PropertyRange(min = 0.0) val glideHalfLifeSeconds: Float = 0.05f,
    @PropertyRange(min = 0.0, max = 1.0) val blendShare: Float = 0.2f,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        fun check(valid: Boolean, field: String) {
            if (!valid) add(SceneValidationIssue("$path.$field", "predicted_entity.$field is outside its valid range"))
        }
        check(correctMetres.isFinite() && correctMetres >= 0f, "correctMetres")
        check(farMetres.isFinite() && farMetres >= correctMetres, "farMetres")
        check(trustedGuessedTicks >= 0, "trustedGuessedTicks")
        check(serverGuessWindowSeconds.isFinite() && serverGuessWindowSeconds >= 0.0, "serverGuessWindowSeconds")
        check(capEaseSeconds.isFinite() && capEaseSeconds >= 0.0, "capEaseSeconds")
        val cap = predictionCapSeconds ?: (serverGuessWindowSeconds + capEaseSeconds)
        check(cap.isFinite() && cap > 0.0, "predictionCapSeconds")
        check(historyCapacity > 0, "historyCapacity")
        check(glideHalfLifeSeconds.isFinite() && glideHalfLifeSeconds >= 0f, "glideHalfLifeSeconds")
        check(blendShare.isFinite() && blendShare in 0f..1f, "blendShare")
    }
}
