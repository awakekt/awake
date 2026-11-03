/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.net.prediction.PredictedEntity
import com.awakekt.awake.net.prediction.PredictionHistory
import com.awakekt.awake.net.prediction.ReconcileConfig
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import kotlin.test.Test
import kotlin.test.assertEquals

/** Fails when a new capability setting has neither a scene field nor a code-only decision. */
@OptIn(ExperimentalSerializationApi::class)
class PredictionExposureTest {
    @Test
    fun everyCapabilitySettingHasASceneField() {
        fun properties(type: Class<*>): Set<String> = type.methods
            .filter { it.declaringClass == type && it.parameterCount == 0 && it.name.startsWith("get") && !it.isSynthetic }
            .map { it.name.removePrefix("get").replaceFirstChar(Char::lowercase) }.toSet()
        // Public runtime state and presentation outputs, not authored options. Private storage and
        // decay implementation names are deliberately absent: this tests the capability's API.
        val poseState = setOf("position", "owed", "speedScale", "drawnX", "drawnY", "drawnZ")
        val configSettings = properties(ReconcileConfig::class.java)
        val poseSettings = properties(PredictedEntity::class.java) - poseState
        val historySettings = properties(PredictionHistory::class.java) - setOf("newestSeq")
        assertEquals(setOf("glideHalfLifeSeconds", "blendShare"), poseSettings)
        assertEquals(setOf("capacity"), historySettings)
        val sceneFields = ScenePredictedEntity.serializer().descriptor.elementNames.toSet()
        assertEquals(sceneFields, configSettings + poseSettings + setOf("historyCapacity"))
        // ReconcileRules is code-only: a callback over the game's state. Snapshots, sequences,
        // timestamps and the presentation entity are supplied by the game, not authored settings.
    }
}
