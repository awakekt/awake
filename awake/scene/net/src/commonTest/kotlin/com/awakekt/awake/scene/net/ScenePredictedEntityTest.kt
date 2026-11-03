/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.net.prediction.ReconcileConfig
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScenePredictedEntityTest {
    @Test
    fun sceneSettingsRoundTripAndAttachWithEveryOptionMapped() {
        val authored = ScenePredictedEntity(
            correctMetres = 0.75f, farMetres = 12f, trustedGuessedTicks = 4,
            serverGuessWindowSeconds = 0.6, capEaseSeconds = 0.3, predictionCapSeconds = 1.2,
            planar = true, historyCapacity = 64, glideHalfLifeSeconds = 0.1f, blendShare = 0.3f,
        )
        val registry = SceneComponentRegistry.scoped().registerNet()
        val json = registry.sceneJson()
        val document = SceneDocument(name = "Harbor Town", nodes = listOf(SceneNode(name = "Local", components = listOf(authored))))
        val text = SceneLoader.encode(document, json)
        assertTrue("predicted_entity" in text)
        val decoded = SceneLoader.decode(text, json).nodes.single().components.single() as ScenePredictedEntity
        assertEquals(authored, decoded)
        val world = World()
        val entity = world.create()
        val context = object : SceneResolutionContext {
            override val world = world
            override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit
            override fun recordRequest(request: Any) = Unit
        }
        assertTrue(registry.resolve(world, entity, decoded, context))
        val live = assertNotNull(world.get<PredictedTransform>(entity))
        assertEquals(ReconcileConfig(0.75f, 12f, 4, 0.6, 0.3, 1.2, true), live.config)
        assertEquals(64, live.history.capacity)
        assertEquals(0.1f, live.glideHalfLifeSeconds)
        assertEquals(0.3f, live.blendShare)
        assertEquals(-1L, live.history.newestSeq)
        assertEquals(authored, PredictedTransformBinding.export(world, entity, live))
        assertEquals(listOf(authored), registry.exportComponents(world, entity))
    }

    @Test
    fun defaultSettingsMatchTheCapabilityAndTheCapFollowsAuthoredTiming() {
        val live = ScenePredictedEntity().toPrediction()
        assertEquals(ReconcileConfig(), live.config)
        assertEquals(ScenePredictedEntity(), live.toScene())
        assertEquals(1.25, ScenePredictedEntity(serverGuessWindowSeconds = 1.0).toPrediction().config.predictionCapSeconds)
    }

    @Test
    fun invalidAndNonFiniteSettingsAreReportedAtTheirField() {
        val invalid = listOf(
            ScenePredictedEntity(correctMetres = -1f), ScenePredictedEntity(correctMetres = Float.NaN),
            ScenePredictedEntity(farMetres = 1f), ScenePredictedEntity(farMetres = Float.POSITIVE_INFINITY),
            ScenePredictedEntity(trustedGuessedTicks = -1), ScenePredictedEntity(serverGuessWindowSeconds = -1.0),
            ScenePredictedEntity(capEaseSeconds = Double.NaN), ScenePredictedEntity(predictionCapSeconds = 0.0),
            ScenePredictedEntity(serverGuessWindowSeconds = 0.0, capEaseSeconds = 0.0),
            ScenePredictedEntity(historyCapacity = 0), ScenePredictedEntity(glideHalfLifeSeconds = -1f),
            ScenePredictedEntity(blendShare = 1.1f), ScenePredictedEntity(blendShare = Float.NaN),
        )
        for (settings in invalid) assertTrue(settings.validate("node").isNotEmpty(), settings.toString())
        assertTrue(ScenePredictedEntity().validate("node").isEmpty())
        assertFailsWith<IllegalArgumentException> { PredictedTransform(blendShare = -1f) }
        assertFailsWith<IllegalArgumentException> { PredictedTransform(glideHalfLifeSeconds = Float.NaN) }
    }
}
