/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toSceneComponent
import com.awakekt.awake.ui.canvas.CanvasCluster
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasLayout
import com.awakekt.awake.ui.canvas.CanvasLook
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** A `canvas_element`'s `look` and `cluster`, read, saved and checked. */
class CanvasLookSceneTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aLookAreaAndAClusterRoundTrip() {
        val area = SceneCanvasElement(
            kind = CanvasElementKind.LookArea,
            action = "look",
            look = CanvasLook(sensitivity = 1.5f, invertX = true, invertY = true, pinchZoom = false, zoomSensitivity = 2f, turnsCamera = false, zoomAction = "zoom"),
        )
        val fan = SceneCanvasElement(
            kind = CanvasElementKind.Panel,
            cluster = CanvasCluster(pivotX = 0.8f, pivotY = 0.9f, radius = 120f, startAngle = 200f, spacing = -35f, primaryAtPivot = false),
        )

        for (saved in listOf(area, fan)) {
            assertEquals(saved, saved.toComponent().toSceneComponent())
            assertEquals(saved, json.decodeFromString(SceneCanvasElement.serializer(), Json.encodeToString(SceneCanvasElement.serializer(), saved)))
        }
        assertEquals(CanvasElementKind.LookArea, json.decodeFromString(SceneCanvasElement.serializer(), """{ "kind": "LookArea" }""").kind)
    }

    @Test
    fun validationNamesWhatIsWrongAndWhereItDoesNothing() {
        val issues = SceneCanvasElement(
            kind = CanvasElementKind.Panel,
            look = CanvasLook(sensitivity = -1f),
            cluster = CanvasCluster(radius = -2f),
            layout = CanvasLayout(),
        ).validate("nodes[0]").map { it.message }

        assertEquals(
            listOf(
                "canvas_element.look only sets up a LookArea",
                "canvas_element.cluster and layout both place the children; keep one",
                "canvas_element.look.sensitivity must not be negative",
                "canvas_element.cluster.radius must not be negative",
            ),
            issues,
        )
    }
}
