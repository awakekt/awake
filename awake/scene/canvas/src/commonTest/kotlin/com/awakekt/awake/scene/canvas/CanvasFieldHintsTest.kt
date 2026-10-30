/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toSceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasPadding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A canvas element's `placeholder`, `padding` and `tooltip`: their defaults, validation and saved form. */
class CanvasFieldHintsTest {
    private val registry = SceneComponentRegistry.scoped(bindings = listOf(CanvasElementBinding))

    private fun issues(element: SceneCanvasElement) = element.validate("n").map { it.message }

    @Test
    fun aSceneSavedBeforeTheseFieldsLoadsWithTheDefaults() {
        val old = """
            { "version": 1, "name": "hud", "nodes": [
              { "name": "Chat", "components": [ { "component": "canvas_element", "kind": "TextField", "text": "Harbor Town" } ] }
            ] }
        """.trimIndent()

        val element = SceneLoader.decode(old, registry.sceneJson()).nodes.single().components.single() as SceneCanvasElement

        assertEquals("", element.placeholder)
        assertEquals("", element.tooltip)
        assertEquals(CanvasPadding(horizontal = 12f, vertical = 0f), element.padding)
        assertEquals(emptyList(), issues(element))
        assertEquals(CanvasPadding(12f, 0f), element.toComponent().padding)
    }

    @Test
    fun theSavedFormRoundTripsThem() {
        val saved = SceneCanvasElement(
            kind = CanvasElementKind.TextField,
            placeholder = "chat.hint",
            padding = CanvasPadding(horizontal = 20f, vertical = 3f),
            tooltip = "chat.tip",
        )
        val document = SceneDocument(name = "hud", nodes = listOf(SceneNode(name = "Chat", components = listOf(saved))))

        val json = SceneLoader.encode(document, registry.sceneJson())

        assertEquals(saved, saved.toComponent().toSceneComponent(), "through the live element")
        assertEquals(document, SceneLoader.decode(json, registry.sceneJson()), "through the scene file")
        val live = saved.toComponent()
        assertEquals("chat.hint", live.placeholder)
        assertEquals(CanvasPadding(20f, 3f), live.padding)
        assertEquals("chat.tip", live.tooltip)
    }

    @Test
    fun theDefaultPaddingRoundTripsToo() {
        val saved = SceneCanvasElement(kind = CanvasElementKind.TextField)
        val document = SceneDocument(name = "hud", nodes = listOf(SceneNode(name = "Chat", components = listOf(saved))))

        val decoded = SceneLoader.decode(SceneLoader.encode(document, registry.sceneJson()), registry.sceneJson())

        assertEquals(document, decoded)
    }

    @Test
    fun validationAcceptsThemWhereTheyApply() {
        assertEquals(emptyList(), issues(SceneCanvasElement(kind = CanvasElementKind.TextField, placeholder = "chat.hint", padding = CanvasPadding(8f, 2f))))
        assertEquals(emptyList(), issues(SceneCanvasElement(kind = CanvasElementKind.Button, textKey = "menu.start", tooltip = "menu.start.tip")))
        assertEquals(emptyList(), issues(SceneCanvasElement(kind = CanvasElementKind.Panel, tooltip = "hud.panel")))
        assertEquals(
            emptyList(),
            issues(SceneCanvasElement(kind = CanvasElementKind.TextField, placeholder = "chat.hint", textArgs = mapOf("name" to "Ana"))),
            "arguments fill in a placeholder's text",
        )
        assertEquals(emptyList(), issues(SceneCanvasElement(tooltip = "hud.tip", textArgs = mapOf("count" to "2"))), "and a tooltip's")
    }

    @Test
    fun validationReportsWhatCannotWork() {
        assertEquals(
            listOf("canvas_element.padding.horizontal must be zero or more"),
            issues(SceneCanvasElement(kind = CanvasElementKind.TextField, padding = CanvasPadding(horizontal = -1f))),
        )
        assertEquals(
            listOf("canvas_element.padding.vertical must be zero or more"),
            issues(SceneCanvasElement(kind = CanvasElementKind.TextField, padding = CanvasPadding(vertical = Float.NaN))),
        )
        assertEquals(
            listOf("canvas_element.placeholder only hints a TextField's empty field"),
            issues(SceneCanvasElement(kind = CanvasElementKind.Text, placeholder = "chat.hint")),
        )
        assertEquals(
            listOf("canvas_element.padding only pads a TextField"),
            issues(SceneCanvasElement(kind = CanvasElementKind.Panel, padding = CanvasPadding(4f, 4f))),
        )
        assertEquals(listOf("canvas_element.tooltip must not be blank"), issues(SceneCanvasElement(tooltip = "  ")))
        assertEquals(listOf("canvas_element.placeholder must not be blank"), issues(SceneCanvasElement(kind = CanvasElementKind.TextField, placeholder = " ")))
    }

    @Test
    fun aPrefabBuiltInCodeSetsThem() {
        val prefab = canvasPrefab(guid = "c0ffee00-0000-4000-8000-000000000002", name = "Chat", id = "chat") {
            textField("Line", id = "line") {
                placeholder = "chat.hint"
                padding = CanvasPadding(horizontal = 6f, vertical = 0f)
                tooltip = "chat.tip"
            }
        }

        val line = assertNotNull(assertNotNull(prefab.findCanvasNode("line")).canvasElementView())

        assertEquals("chat.hint", line.placeholder)
        assertEquals(CanvasPadding(6f, 0f), line.padding)
        assertEquals("chat.tip", line.tooltip)
    }

    @Test
    fun theLiveElementsDefaultsAreTheSavedFormsDefaults() {
        val live = CanvasElement()
        val saved = SceneCanvasElement()

        assertEquals(saved.placeholder, live.placeholder)
        assertEquals(saved.padding, live.padding)
        assertEquals(saved.tooltip, live.tooltip)
    }
}
