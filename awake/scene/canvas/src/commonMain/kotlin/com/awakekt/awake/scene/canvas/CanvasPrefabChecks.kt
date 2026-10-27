/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

// What the prefab builder reports beyond what the saved components validate: a setting its element's
// kind never uses, such as a Panel's text. The element is saved as the components its kind is made
// of, so such a setting would be dropped without a word; it is reported instead, so a builder that
// set it learns it does nothing.

private val fieldsJson = Json { encodeDefaults = true }

/**
 * One issue at [path] for each field of this element that its components do not keep: a field its
 * kind does nothing with, which saving would drop.
 */
internal fun SceneCanvasElement.unsavedFieldIssues(path: String): List<SceneValidationIssue> {
    val kept = SceneNode(components = toCanvasComponents()).canvasElementView() ?: return emptyList()
    val mine = fieldsJson.encodeToJsonElement(SceneCanvasElement.serializer(), this).jsonObject
    val saved = fieldsJson.encodeToJsonElement(SceneCanvasElement.serializer(), kept).jsonObject
    return mine.keys.filter { mine[it] != saved[it] }.map { field ->
        SceneValidationIssue(path, "$field does nothing on a ${kind.name}, so it is not saved")
    }
}
