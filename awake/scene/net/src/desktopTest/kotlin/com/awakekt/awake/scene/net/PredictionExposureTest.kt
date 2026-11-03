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
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

/** Fails when a new capability setting has neither a scene field nor a code-only decision. */
@OptIn(ExperimentalSerializationApi::class)
class PredictionExposureTest {
    @Test
    fun everyCapabilitySettingHasASceneField() {
        fun fields(type: Class<*>): Set<String> = type.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }.map { it.name }.toSet()
        // Code-only runtime state, not options: poses, drift, speed, visual decay and ring storage.
        val poseState = setOf("position", "owed", "speedScale", "glide")
        val historyState = setOf("seqs", "xs", "ys", "zs", "times", "newestSeq")
        val configSettings = fields(ReconcileConfig::class.java)
        val poseSettings = fields(PredictedEntity::class.java) - poseState
        val historySettings = fields(PredictionHistory::class.java) - historyState
        assertEquals(setOf("glideHalfLifeSeconds", "blendShare"), poseSettings)
        assertEquals(setOf("capacity"), historySettings)
        val sceneFields = ScenePredictedEntity.serializer().descriptor.elementNames.toSet()
        assertEquals(sceneFields, configSettings + poseSettings + setOf("historyCapacity"))
        // ReconcileRules is code-only: a callback over the game's state. Snapshots, sequences,
        // timestamps and the presentation entity are supplied by the game, not authored settings.
    }
}
