/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.director

import com.awakekt.awake.director.DirectorScript
import com.awakekt.awake.director.LensSpec
import com.awakekt.awake.scene.document.SceneVec3
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Schema exposure contract for `:awake:scene:director`: verifies that every field on the scene
 * document schemas ([SceneCameraDirector], [SceneVirtualCamera], [SceneCameraRail],
 * [SceneStageMarker]) is accounted for, bound in [DirectorSceneComponents], and carries across
 * cleanly to its runtime ECS component.
 */
@OptIn(ExperimentalSerializationApi::class)
class DirectorExposureTest {
    @Test
    fun allFourDirectorSceneComponentsAreRegistered() {
        val schemaClasses = DirectorSceneComponents.bindings.map { it.schemaClass }.toSet()
        assertEquals(
            setOf(
                SceneCameraDirector::class,
                SceneVirtualCamera::class,
                SceneCameraRail::class,
                SceneStageMarker::class,
            ),
            schemaClasses,
        )
    }

    @Test
    fun directorSchemaFieldsMatchDocumentedContract() {
        assertEquals(
            setOf("scriptPath", "inlineScript", "inputTakePath", "autoPlay", "loop", "priority"),
            SceneCameraDirector.serializer().descriptor.elementNames.toSet(),
        )
        assertEquals(
            setOf("eye", "center", "lens", "priority"),
            SceneVirtualCamera.serializer().descriptor.elementNames.toSet(),
        )
        assertEquals(
            setOf("waypoints", "closed", "alpha", "localSpace"),
            SceneCameraRail.serializer().descriptor.elementNames.toSet(),
        )
        assertEquals(
            setOf("tags", "radius"),
            SceneStageMarker.serializer().descriptor.elementNames.toSet(),
        )
    }

    @Test
    fun schemaValidatorsRejectInvalidConfigurationsAndAcceptValidOnes() {
        assertTrue(SceneCameraDirector().validate("director").isNotEmpty())
        assertTrue(SceneCameraDirector(scriptPath = "scripts/trailer.director.json").validate("director").isEmpty())
        assertTrue(SceneCameraDirector(inlineScript = DirectorScript(name = "empty")).validate("director").isNotEmpty())

        assertTrue(SceneVirtualCamera(lens = LensSpec(focalLengthMm = -10f)).validate("vcam").isNotEmpty())
        assertTrue(SceneVirtualCamera(lens = LensSpec(focalLengthMm = 50f)).validate("vcam").isEmpty())

        assertTrue(SceneCameraRail(waypoints = listOf(SceneVec3(0f, 0f, 0f))).validate("rail").isNotEmpty())
        assertTrue(
            SceneCameraRail(
                waypoints = listOf(SceneVec3(0f, 0f, 0f), SceneVec3(0f, 0f, 10f)),
            ).validate("rail").isEmpty(),
        )

        assertTrue(SceneStageMarker(radius = 0f).validate("marker").isNotEmpty())
        assertTrue(SceneStageMarker(tags = listOf("vista"), radius = 2f).validate("marker").isEmpty())
    }
}
